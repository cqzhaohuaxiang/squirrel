package com.android.squirrel.service



import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.android.squirrel.R
import com.android.squirrel.tools.MacroDatabaseHelper
import com.android.squirrel.tools.SharedDataPermanently
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong


class SendServices : Service() {

    private lateinit var socketJob: Job
    private lateinit var dataQueueJob: Job
    private val binder = LocalBinder()
    private lateinit var dbHelper: MacroDatabaseHelper
    private lateinit var db: SQLiteDatabase

    private val reporterSymbolsQueue: LinkedBlockingQueue<ByteArray> = LinkedBlockingQueue(62) // 报告符数据网络发送队列
    private val insertDataQueue: LinkedBlockingQueue<Pair<String, Pair<ByteArray, Long>>> = LinkedBlockingQueue()//报告符数据写入数据库队列

    private var errMsg = ""  //如果有错误写入这儿

    private var isToastVisible = false  // 标记 Toast 是否正在显示
    private var currentToast: Toast? = null  // 当前显示的 Toast
    private var currentTime: Long = 0
    private var hostAddress : InetAddress? = null   //服务的ip地址
    private var isRunning = true

    //接收wifi关闭的情况下
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
            if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                hostAddress = null
                errMsg = getString(R.string.wifiConnected)
                showToast(errMsg)
                isRunning = false

            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): SendServices = this@SendServices
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    // Service 启动时的回调

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 创建前台通知
        // 判断当前 Android 版本，只有在 Android 8.0 及以上版本时才创建 NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 在 Android 8.0 及以上版本需要创建 NotificationChannel
            val channel = NotificationChannel(
                "udp_service_channel", "UDP Service", NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // 创建通知
        val notification: Notification = NotificationCompat.Builder(this, "udp_service_channel")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.running))
            .setSmallIcon(R.drawable.app_icon) // 自定义的图标
            .build()

        // 将服务设为前台服务
        startForeground(1, notification)

        // 保持服务持续运行，直到显式停止
        return START_STICKY
    }




    override fun onCreate() {
        super.onCreate()

        // 初始化数据库助手和数据库
        dbHelper = MacroDatabaseHelper(applicationContext)
        db = dbHelper.writableDatabase

        // 注册 Wi-Fi 状态变化广播
        val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        applicationContext.registerReceiver(wifiReceiver, filter)
        dataQueueJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val (name, data) = insertDataQueue.take()
                val (msg, time) = data
                insertData(name, msg, time) //插入数据库中
            }
        }

        socketJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                starSocket()
                delay(500)
            }
        }
    }

    /** 服务销毁时的回调 */
    override fun onDestroy() {
        super.onDestroy()
        db.close()  // 关闭数据库连接
        dataQueueJob.cancel()
        socketJob.cancel()

    }

    private suspend fun starSocket() {
        withContext(Dispatchers.IO) {
            val broadcastAddress = getWiFiInetAddress(applicationContext)

            if (broadcastAddress != null) {
                val socket = DatagramSocket()  // 创建 UDP 套接字
                isRunning = true
                hostAddress = null
                val serverPort = 36870 // 服务监听端口
                val hiByte = "Hi!".toByteArray()
                socket.broadcast = true   //允许发送广播数据
                val index = AtomicLong(1)  //发送数据的序号
                var volumeSent: Byte = 1   //服务接收数据缓冲队列中的数据
                val receivedFlag = AtomicBoolean(false)  //数据收到标志
                val parameter = SharedDataPermanently("parameter", applicationContext)
                var sendTime = System.currentTimeMillis() //数据发送的时间
                val delayTime = AtomicLong(30)
                socket.soTimeout = 500
                //接收数据
                launch {
                    val receive = ByteArray(512)
                    val packet = DatagramPacket(receive, receive.size)
                    while (isRunning){
                        try {
                            socket.receive(packet)
                            val length = packet.length
                            val received = packet.data
                            val byteBuffer = ByteBuffer.wrap(received, 0, length)
                            val sequenceNumber = byteBuffer.long //序号（8字节）
                            val type = byteBuffer.get()
                            delayTime.set(System.currentTimeMillis() - sendTime)
                            if(type == 44.toByte()){
                                errMsg = getString(R.string.noHost)

                            }else {
                                if(length == 11){
                                    if (calculateChecksum(byteBuffer)) {
                                        volumeSent = byteBuffer.get()
                                        errMsg = "${getString(R.string.connectedOK)} ${packet.address}"
                                        if (sequenceNumber == index.get() && type == 100.toByte()) {
                                            receivedFlag.set(false)
                                            delay(2)
                                            index.set(index.get() + 1)

                                        }
                                    }
                                }else if (String(received, 0, length) == "I am the HID-USB service!"){
                                    hostAddress = packet.address
                                    errMsg = "${getString(R.string.connectedOK)} $hostAddress "
                                }
                            }

                        }
                        catch (e: SocketTimeoutException){
                            //接收数据超时后检查网络状态
                            if(getWiFiInetAddress(applicationContext) != broadcastAddress){
                                hostAddress = null
                                isRunning = false
                            }
                            errMsg = if (hostAddress == null){
                                  getString(R.string.noService)
                            }else getString(R.string.connectedErr)

                        }
                        catch (e: IOException) {
                            //防止socket关闭后还去读出错
                            e.printStackTrace()
                        }
                    }
                }

                try {
                    // 发送数据
                    while (isRunning){
                        if (hostAddress == null ){
                            val directedBroadcast = DatagramPacket(hiByte, hiByte.size, broadcastAddress, serverPort) //定向广播 x.x.x.255
                            socket.send(directedBroadcast)
                            var startTime = System.currentTimeMillis()
                            while (hostAddress == null){
                                delay(10)
                                if (System.currentTimeMillis() - startTime > 100) {
                                    socket.send(directedBroadcast)
                                    startTime = System.currentTimeMillis()
                                }
                            }
                        }
                        else {
                            val retransmission = parameter.getParameter("retransmission", false) as Boolean
                            // 检查队列中是否有数据
                            if (reporterSymbolsQueue.isNotEmpty() && volumeSent > 0) {
                                val rxData = mutableListOf<ByteArray>()
                                //取全部数据
                                reporterSymbolsQueue.drainTo(rxData)
                                //发送的数据是不是接受重传标志  100 重传 其他不重传
                                val type: Byte = if (retransmission) 100 else 50
                                val symbolBuffer = assembleSendData(index.get(),type, rxData) // 组装报告符数据
                                val sendPacket = when(isVpnConnected(applicationContext)){
                                    true -> DatagramPacket(symbolBuffer, symbolBuffer.size, broadcastAddress, serverPort)
                                    false -> DatagramPacket(symbolBuffer, symbolBuffer.size, hostAddress, serverPort)
                                }
                                socket.send(sendPacket)
                                sendTime = System.currentTimeMillis()
                                if (retransmission) {
                                    receivedFlag.set(true)
                                    var startTime = System.currentTimeMillis() // 获取当前时间作为开始时间
                                    while (receivedFlag.get()) {
                                        delay(10)
                                        if (System.currentTimeMillis() - startTime > 100) {
                                            socket.send(sendPacket)
                                            startTime = System.currentTimeMillis()
                                            sendTime = System.currentTimeMillis()
                                        }
                                    }
                                }

                            }
                            else if (System.currentTimeMillis() - sendTime > 400) {
                                //发送心跳包 目的是获得接收的队列数据
                                val heartbeat = assembleSendData(0,78, null)
                                val heartbeatPacket = when(isVpnConnected(applicationContext)){
                                    true -> DatagramPacket(heartbeat, heartbeat.size, broadcastAddress, serverPort)
                                    false -> DatagramPacket(heartbeat, heartbeat.size, hostAddress, serverPort)
                                }
                                socket.send(heartbeatPacket)
                                sendTime = System.currentTimeMillis()
                            }
                            delay(30)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    // 其他 IO 异常处理
                    e.printStackTrace()
                }
                finally {
                    receivedFlag.set(false)
                    isRunning = false
                    hostAddress = null
                    socket.takeIf { !it.isClosed }?.close()
                }
            }

        }
    }

    //检查wifi网络 返回该接口的广播地址
    private fun getWiFiInetAddress(context: Context): InetAddress? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo = wifiManager.connectionInfo
        // 检查 Wi-Fi 是否连接
        if (wifiInfo.networkId == -1) {
            errMsg = getString(R.string.wifiConnected) // Wi-Fi 关闭
            return null // Wi-Fi 未连接
        }

        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        // 遍历所有网络接口
        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            // 获取该接口的所有地址
            val interfaceAddresses = networkInterface.interfaceAddresses
            for (address in interfaceAddresses) {
                val ip = address.address

                // 排除回环地址和通配地址
                if (ip is InetAddress && ip.isSiteLocalAddress && !ip.isLoopbackAddress && ip.hostAddress != "0.0.0.0") {
                    // 如果是有效的 IPv4 地址，返回该接口
                    if (networkInterface.name.contains("wlan", ignoreCase = true) || networkInterface.name.contains("wifi", ignoreCase = true)) {
                        return address.broadcast // 获取该接口的广播地址
                    }
                }
            }
        }
        errMsg = getString(R.string.invalidIP) // 无效的 IP 地址
        return null // 没有找到合适的接口
    }

    //检测 VPN 状态
    private fun isVpnConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

        return networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }



    /**
     * 数据格式：
     * 发送的数据类型            1字节
     * 序号                    8字节
     * 内容                    不确定
     * 校验和                  1字节
     * */
    private fun assembleSendData(
        index: Long,       // 序号
        type: Byte,        // 数据类型
        content: MutableList<ByteArray>?  // 内容部分，可以为 null
    ): ByteArray {
        // 如果 content 为 null，则默认为空列表
        val safeContent = content ?: mutableListOf()
        // 计算内容部分的总字节数
        val contentLength = safeContent.sumOf { it.size }
        // 数据包总长度
        val totalLength = 10 + contentLength
        // 创建 ByteBuffer，分配足够空间
        val buffer = ByteBuffer.allocate(totalLength)
        // 存入数据类型
        buffer.put(type)           // 1字节：数据类型
        // 存入序号
        buffer.putLong(index)      // 8字节：序号
        // 存入内容
        for (byteArray in safeContent) {
            buffer.put(byteArray)  // 存入内容部分
        }

        // 计算校验和：将数据（不包括校验和）从 buffer 中取出
        val dataWithoutChecksum = buffer.array().copyOfRange(0, buffer.position())  // 获取数据部分
        val checksum = dataWithoutChecksum.sum().toByte()  // 校验和：所有字节的和（低8位）

        // 将校验和存入 buffer（校验和为最后一个字节）
        buffer.put(checksum)  // 1字节：校验和

        // 准备读取数据
        buffer.flip()
        // 返回最终的字节数组

        return buffer.array()
    }



    // 计算校验和
    private fun calculateChecksum(buffer: ByteBuffer): Boolean {
        // 获取原始校验和在数据的最后1位
        val position = buffer.limit() - 1 // 获取最后一个字节的位置
        val expectedChecksum = buffer.get(position) // 获取最后的校验和

        // 将 buffer 中的校验和位置设置为 0，准备进行计算
        buffer.put(position, 0.toByte())

        // 初始化校验和计算
        var checksum: Byte = 0

        // 遍历 ByteBuffer 中的数据进行累加
        for (i in 0 until buffer.limit()) {
            checksum = ((checksum + buffer.get(i)).toByte()) // 累加每个字节
        }

        // 将计算出的校验和放回到 buffer 中的校验和位置
        buffer.put(position, checksum)

        // 检查计算的校验和是否与存储的校验和匹配
        return checksum == expectedChecksum
    }




    /****数据库有关的****/
    // 插入数据到指定表
    private fun insertData(tableName: String, byteArray: ByteArray, timestamp: Long): Boolean {
        return dbHelper.insertData(db, tableName, byteArray, timestamp)
    }

    // 创建新表
    fun createNewTable(tableName: String): Boolean {
        return dbHelper.createNewTable(db, tableName)
    }

    // 删除指定表
    fun deleteTable(tableName: String): Boolean {
        return dbHelper.deleteTable(db, tableName)
    }

    // 查询所有表名
    fun getAllTableNames(): List<String> {
        return dbHelper.getAllTableNames(db)
    }

    // 获取指定表的所有数据
    fun getDataFromTable(tableName: String): List<Triple<ByteArray, String, Long>> {
        return dbHelper.getData(db, tableName)
    }


    // 显示自定义样式的 Toast
    private fun showToast(message: String) {
        // 如果消息和上次显示的不一样，则立即显示新 Toast
        if (message != errMsg) {
            // 立即显示 Toast 并更新标记
            showToastOnMainThread(message)
            errMsg = message
        } else {
            // 如果消息相同，且没有正在显示的 Toast，则显示新的 Toast
            if (!isToastVisible) {
                showToastOnMainThread(message)
            }
        }
    }

    //显示 Toast
    private fun showToastOnMainThread(message: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            // 如果当前 Toast 已经显示，先取消
            currentToast?.cancel()
            // 创建新的 Toast
            val toast = Toast.makeText(applicationContext, null, Toast.LENGTH_SHORT)
            val layout = toast.view as LinearLayout?
            layout?.setBackgroundResource(R.color.transparent)  // 设置背景色为透明

            val textView = layout?.getChildAt(0) as TextView
            textView.textSize = 20f  // 设置字体大小
            textView.setTextColor(applicationContext.getColor(R.color.yellow))  // 设置字体颜色
            toast.setGravity(Gravity.CENTER, 0, 0)  // 设置显示位置为屏幕中央
            toast.setText(message)  // 设置显示的文本
            toast.show()

            // 保存当前 Toast 引用并标记为正在显示
            currentToast = toast
            isToastVisible = true

            // 设置延迟，Toast 显示结束后重置标记
            Handler(Looper.getMainLooper()).postDelayed({
                isToastVisible = false  // 重置标记，表示 Toast 显示完毕
            }, 3000)  //延迟时间
        }
    }

    
    fun clearQueue(key: ByteArray ,mouse: ByteArray){
        reporterSymbolsQueue.clear()
        reporterSymbolsQueue.put(key)
        reporterSymbolsQueue.put(mouse)
    }
    fun setReporterData(data: ByteArray, name: String): Boolean {
        var success = false
        if (hostAddress != null) {
            //如果是鼠标的报告符，则最后两们写入0xff  ,键盘报告符不做修改
            if (data.size == 5) {
                val result = ByteArray(8)
                val buffer = ByteBuffer.wrap(result)
                buffer.put(data.copyOf())
                buffer.put(0)
                buffer.put(0xFF.toByte())                // 填充0xFF   鼠标数据标志
                buffer.put(0xFF.toByte())                // 填充0xFF
                success = reporterSymbolsQueue.offer(result, 100, TimeUnit.MILLISECONDS)
            } else {
                success = reporterSymbolsQueue.offer(data.copyOf(), 100, TimeUnit.MILLISECONDS)
            }

            if (success && name.isNotEmpty()) {
                //计算时间
                if (currentTime == 0L) currentTime = System.currentTimeMillis()
                val timeStamp = System.currentTimeMillis() - currentTime
                currentTime = System.currentTimeMillis()
                val dataToInsert = Pair(name, Pair(data.copyOf(), timeStamp))
                insertDataQueue.offer(dataToInsert, 100, TimeUnit.MILLISECONDS)
            } else {
                currentTime = 0L
//                errMsg = getString(R.string.failedEnqueue)

            }
        } else {
            showToast(errMsg)
        }

        return success
    }

    private fun printHex(buffer: ByteArray) {
        println(buffer.joinToString(" ") { byte -> String.format("%02X", byte) })
    }

}