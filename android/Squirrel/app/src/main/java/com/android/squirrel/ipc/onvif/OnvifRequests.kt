package com.android.squirrel.ipc.onvif


import android.content.Context
import android.content.res.AssetManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 *避免回调地狱 采用挂起函数
 * **/

class OnvifRequests (val context: Context): ViewModel(){

    private  var wsJob: Job? = null //用于发现设备的协程任务
    private  var wsSocket: DatagramSocket? = null
    private val reporterQueue: LinkedBlockingQueue<Pair<String, DatagramPacket>> = LinkedBlockingQueue()

    private val nameRegex =  """(?<=name/)[^/]+""".toRegex()//取出名称字段

    //ws 发现过的设备存入这个中
    private val deviceMap: MutableMap<String, OnvifDeviceMsg> = mutableMapOf()
    //通知ui更新显示的设备
    private val _display = MutableLiveData<Map<String, DeviceDisplayInformation>>()
    val display: LiveData<Map<String, DeviceDisplayInformation>> get() = _display

    // 用于通知 UI 处理结果
    private var _request = MutableLiveData<ReturnOperation>()
    val request: LiveData<ReturnOperation> get() = _request

    init {
        wsDiscovery()

    }
    /**
     * 用于WS发现本地网络中所有 ONVIF 设备的函数。
     */

    @OptIn(DelicateCoroutinesApi::class)
    private fun wsDiscovery() {
        if (wsJob?.isActive == true) return
        wsJob = GlobalScope.launch(Dispatchers.IO) {
            if (getWiFiInetAddress(context)){
                try {
                    // 创建 DatagramSocket
                    wsSocket = DatagramSocket()
                    wsSocket?.broadcast = true  //允许广播




                    //缓慢处理接收到的数据
                    launch {
                        while (isActive){
                            //key 的数据是发送方的ip与端口
                            val (key, receivedPacket) = reporterQueue.take()

                            //没有解析过？  解析ws发现返回数据
                            if (!deviceMap.containsKey(key)){
                                val xml = String(receivedPacket.data, 0, receivedPacket.length)

                                val newDisplay: Map<String, DeviceDisplayInformation> =  mapOf(
                                    key to DeviceDisplayInformation(key, "","onvif"))

                                val device = OnvifDeviceMsg("","","","","","","")
                                deviceMap[key] = device

                                val targets = listOf("d:Scopes", "d:XAddrs")  // 关心的元素标签名
                                val result = parseXml(xml, targets)

                                val scopes = result["d:Scopes"]
                                if (scopes != null) {
                                    for (scope in scopes) {
                                        val name = nameRegex.find(scope)?.value ?: ""
                                        if (name != "") {
                                            newDisplay[key]?.deviceName = name.toString()
                                            break
                                        }
                                    }
                                }

                                val addrs = result["d:XAddrs"]
                                if (addrs != null) {
                                    for (address in addrs) {
                                        val ipv4Address = extractIPv4(address)
                                        if (ipv4Address != ""){
                                            newDisplay[key]?.deviceIp = ipv4Address
                                            deviceMap[key]?.deviceAddress = address
                                            break
                                        }
                                    }
                                }

                                // 更新 UI，切换到主线程
                                withContext(Dispatchers.Main) {
                                    _display.value = newDisplay
                                }

                            }
                        }
                    }

                    //这个就只管接收数据，送到队列中
                    val receiveBuffer = ByteArray(1024*20)  // 缓冲区大小
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    launch {
                        while (isActive){
                            try {
                                wsSocket?.receive(packet)
//                                val senderAddress = (packet.socketAddress as InetSocketAddress).address.hostAddress?.toString()//不带端口号
                                val address = packet.socketAddress.toString() //含有端口号
                                val packetCopy = DatagramPacket(packet.data.copyOf(), packet.length)
                                reporterQueue.put(Pair(address, packetCopy))

                            }catch (e: SocketException) {
                                e.printStackTrace()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }


                    val broadcastAddress = InetAddress.getByName("239.255.255.250");  // ONVIF 多播地址
                    val  port = 3702;  // ONVIF 默认端口
                    while (isActive){
//                        val msg=  getWsDiscovery()
                        val targets = listOf("${UUID.randomUUID()}")
                        val msg = getSendMsg("xml/WsDiscovery",targets)
                        val msgByte = msg.toByteArray()
                        val sendPacket = DatagramPacket(msgByte, msgByte.size, broadcastAddress, port)
                        wsSocket?.send(sendPacket)
                        delay(1500)
                    }

                } catch (e: SocketException) {
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stop(){
        wsSocket?.close()
        deviceMap.clear()
        wsJob?.cancel()
    }



    fun getPlayUri(key:String): String {
        if (deviceMap.containsKey(key)){
            return deviceMap[key]?.uri ?:""
        }
        return ""
    }

    /***
     * 不采用回调与监听的方式  全部采用挂起的方式
     * 以设备的时间为准进行通信
     * **/
    fun getUri (username:String, password:String, key:String){
        CoroutineScope(Dispatchers.IO).launch {
            if (deviceMap.containsKey(key)){
                //步骤 1  获得设备的时间
                val deviceTime = getDeviceTime(username,password,key)

                getServices(username,password,deviceTime,key)
//                查询媒体服务地址有没有获取到
                if (deviceMap[key]?.mediaAddress != ""){
                    //获取媒体配置文件
                    getProfiles(username,password,deviceTime,key)
                    //获取媒体地址
                    getStreamUri(username,password,deviceTime,key)
                }


            }
        }

    }
    fun setPtz (key:String,data :PtzData){
        CoroutineScope(Dispatchers.IO).launch {
            if (deviceMap.containsKey(key)){
                //查询PTZ服务地址有没有获取到
                if (deviceMap[key]?.ptzAddress != ""){
                    val username = deviceMap[key]?.username
                    val password = deviceMap[key]?.password
                    val deviceTime = username?.let { password?.let { it1 -> getDeviceTime(it,it1,key) } }
                    username?.let { password?.let { it1 -> deviceTime?.let { date1 -> putPtz(it,it1,date1,key,data) } } }
                }
            }
        }

    }

    //这个采用回调的方式
    fun timeSync(key: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (deviceMap.containsKey(key)){
                val address = deviceMap[key]?.deviceAddress
                address?.let {
                    // 获取当前时间
                    val calendar = Calendar.getInstance()

                    // 获取时间组件
                    val year = calendar.get(Calendar.YEAR).toString()
                    val month = (calendar.get(Calendar.MONTH) + 1).toString() // Calendar.MONTH 是从 0 开始的
                    val day = calendar.get(Calendar.DAY_OF_MONTH).toString()
                    val hour = calendar.get(Calendar.HOUR_OF_DAY) .toString() // 24小时制
                    val minute = calendar.get(Calendar.MINUTE).toString()
                    val second = calendar.get(Calendar.SECOND).toString()

                    val username = deviceMap[key]?.username ?: "username"
                    val password = deviceMap[key]?.password ?: "password"
                    val deviceTime = getDeviceTime(username,password,key)
                    val nonce = getNonce()
                    val passwordDigest = getPasswordDigest(nonce, deviceTime,password)
                    //标准的请求头
                    val targets = listOf(username,passwordDigest,nonce,deviceTime
                       ,hour,minute,second,year,month,day)
                    val sendMsg = getSendMsg("xml/SetSystemDateAndTime",targets)
                    val xml  = sendPostRequest(address,sendMsg)

                    withContext(Dispatchers.Main) {
                        if (xml != ""){
                            callback("yes")
                        }else  callback("no")
                    }


                }

            }

        }
    }

    fun checkDeviceTime(key: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (deviceMap.containsKey(key)){
                //步骤 1  获得设备的时间
                val username = deviceMap[key]?.username ?: "username"
                val password = deviceMap[key]?.password ?: "password"
                val deviceTime = getDeviceTime(username,password,key)
                withContext(Dispatchers.Main) {
                    callback(deviceTime)
                }
            }
        }
    }
    fun getOSD(key: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (deviceMap.containsKey(key)){
                val profile = deviceMap[key]?.profileToken
                profile?.let {
                    //步骤 1  获得设备的时间
                    val username = deviceMap[key]?.username ?: "username"
                    val password = deviceMap[key]?.password ?: "password"
                    val deviceTime = getDeviceTime(username,password,key)
                    val nonce = getNonce()
                    val passwordDigest = getPasswordDigest(nonce, deviceTime,password)
                    //标准的请求头
                    val targets = listOf(username,passwordDigest,nonce,deviceTime,profile)
                    val sendMsg = getSendMsg("xml/GetOSDs",targets)

                    val address = deviceMap[key]?.mediaAddress
                    val xml  = address?.let { urlString -> sendPostRequest(urlString,sendMsg) }

                    if (xml != ""){
                        val tags = listOf("tt:PlainText")
                        val result = parseXml(xml!!, tags)

                        val osd = result["tt:PlainText"]?.joinToString(" ")
                        withContext(Dispatchers.Main) {
                            osd?.let { p1 -> callback(p1) }
                        }
                    }

                }

            }
        }
    }



    //获取设备的时间
    suspend  fun getDeviceTime(username:String, password:String,key: String): String{
        val address = deviceMap[key]?.deviceAddress
        address?.let {
            val nonce = getNonce()
            val date = getDate()
            val passwordDigest = getPasswordDigest(nonce, date,password)
            //标准的请求头
            val targets = listOf(username,passwordDigest,nonce,date)
            val sendMsg = getSendMsg("xml/GetSystemDateAndTime",targets)
            val xml  = sendPostRequest(address,sendMsg)

            if (xml != "")  return  parseTime(xml)
        }

        return ""
    }
    //设备的服务地址，GetServices
    suspend fun getServices(username:String, password:String,date: String,key:String){

        val address = deviceMap[key]?.deviceAddress
        address?.let {
            val nonce = getNonce()
            val passwordDigest = getPasswordDigest(nonce, date,password)

            //标准的请求头
            val sendMsg = getSendMsg("xml/GetServices",listOf(username,passwordDigest,nonce,date))

            val addrs = sendPostRequest(address,sendMsg)
            val result = parseXml(addrs,  listOf("tds:XAddr"))
            val scopes = result["tds:XAddr"]
            if (scopes != null) {
                for (scope in scopes) {
                    //只保存关心的服务地址 目前只有媒体服务与 ptz
                    when{
                        scope.contains("Media") ->  deviceMap[key]?.mediaAddress = scope
                        scope.contains("PTZ") -> deviceMap[key]?.ptzAddress = scope
                    }
                }
            }
        }


    }
    //获取媒体配置文件key  修改只获取主码流
    suspend fun getProfiles(username:String, password:String,date: String,key:String){
        val address = deviceMap[key]?.mediaAddress
        address?.let {
            val nonce = getNonce()
            val passwordDigest = getPasswordDigest(nonce, date,password)
            val sendMsg = getSendMsg("xml/GetProfiles",listOf(username,passwordDigest,nonce,date))

            val xml = sendPostRequest(address,sendMsg)
            val tags = listOf("trt:Profiles")
            val attributes = listOf("token")
            val result = parseXml(xml, tags, attributes)

//            deviceMap[key]?.profileToken = result["token"] ?: listOf()   // 这个list 中有所有的码流

            // 从解析结果中获取主码流的 profileToken（通常为第一个 token  不想修改解析 parseXml 了）
            val profileToken = result["token"]?.firstOrNull()  // 获取第一个 token
            // 如果存在 profileToken，保存到 deviceMap
            profileToken?.let {
                deviceMap[key]?.profileToken = it
            }

        }
    }
   //获取主码流的地址
    suspend fun getStreamUri(username:String, password:String,date: String,key:String){
        val address = deviceMap[key]?.mediaAddress
        address?.let {
            val profileToken = deviceMap[key]?.profileToken
            profileToken?.let {
                val nonce = getNonce()
                val passwordDigest = getPasswordDigest(nonce, date,password)
                val sendMsg = getSendMsg("xml/GetStreamUri",listOf(username,passwordDigest,nonce,date,profileToken))

                val xml = sendPostRequest(address,sendMsg)
                val tags = listOf("tt:Uri")
                val result = parseXml(xml, tags)

                val uri = result["tt:Uri"]?.firstOrNull()
                uri?.let { it1 ->
                    val urlString = addCredentialsToUri(uri, username, password)
                    deviceMap[key]?.uri = urlString
                    deviceMap[key]?.username = username
                    deviceMap[key]?.password = password
                    //通知UI 播放视频
                    val play = ReturnOperation("PlayVideo",urlString,mapOf("onvif" to key))
                    withContext(Dispatchers.Main) {
                        _request.value = play
                    }
                }
            }
        }
    }

    suspend fun putPtz(username:String, password:String,date: String,key:String,data: PtzData){
        val address = deviceMap[key]?.ptzAddress
        address?.let {
            val profileToken = deviceMap[key]?.profileToken
            profileToken?.let {
                val x = data.x
                val y = data.y
                val zoom = data.zoom
                val nonce = getNonce()
                val passwordDigest = getPasswordDigest(nonce, date,password)
                val sendMsg = getSendMsg("xml/GetStreamUri",listOf(username,passwordDigest,nonce,date,profileToken,x,y,zoom))
                val xml = sendPostRequest(address,sendMsg)

            }
        }
    }



    //检查wifi状态
    private fun getWiFiInetAddress(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo = wifiManager.connectionInfo
        // 检查 Wi-Fi 是否连接
        if (wifiInfo.networkId == -1) return false

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
                    // 如果是有效的 IPv4 地址，返回该接口的 IP 地址
                    if (networkInterface.name.contains("wlan", ignoreCase = true) || networkInterface.name.contains("wifi", ignoreCase = true)) {
                        return true
                    }
                }
            }
        }

        return false // 没有找到合适的接口
    }

    // 将用户名和密码加入到 URI 中
    private fun addCredentialsToUri(uri: String, username: String, password: String): String {
        // 对用户名和密码进行 URL 编码
        val encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8.toString())
        val encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8.toString())

        // 假设 URI 是以 rtsp:// 开头的格式
        return uri.replace("rtsp://", "rtsp://$encodedUsername:$encodedPassword@")
    }


    //解析出时间格式
    private fun parseTime(xmlString: String): String {
        // 定义我们需要解析的标签，注意这里使用命名空间前缀
        val targetElements = listOf("tt:Year", "tt:Month", "tt:Day", "tt:Hour", "tt:Minute", "tt:Second")

        val parsedData = parseXml(xmlString, targetElements)


        // 提取日期和时间的值   get(1)
        val year = parsedData["tt:Year"]?.get(0)?.toIntOrNull() ?: return ""
        val month = parsedData["tt:Month"]?.get(0)?.toIntOrNull()?: return ""
        val day = parsedData["tt:Day"]?.get(0)?.toIntOrNull() ?: return ""
        val hour = parsedData["tt:Hour"]?.get(0)?.toIntOrNull() ?: return ""
        val minute = parsedData["tt:Minute"]?.get(0)?.toIntOrNull() ?: return ""
        val second = parsedData["tt:Second"]?.get(0)?.toIntOrNull() ?: return ""

        // 使用 Calendar 来构建日期时间
        val calendar = Calendar.getInstance()
        calendar.set(year, month-1, day, hour, minute, second)//月份从 0 开始

        // 格式化为 "yyyy-MM-dd'T'HH:mm:ss'Z'" 格式
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
//        sdf.timeZone = TimeZone.getTimeZone("UTC")  // 设置为 UTC 时区
        return sdf.format(calendar.time)  // 返回格式化后的日期时间字符串
    }

    //24位随机数生成
    private fun getNonce(): String {
//         1 生成一个 UUID 字符串，并去掉破折号   字符集: 0123456789abcdef
        return UUID.randomUUID().toString().replace("-", "").take(24)
        //2 字符集:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/
//        val byteArray = ByteArray(24)  // 24字节
//        Random.nextBytes(byteArray)  // 填充随机字节
//        return Base64.getEncoder().encodeToString(byteArray)  // Base64 编码并返回字符串
    }
    //返回24小时制时间
    private fun getDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        return dateFormat.format(Date())
    }

    //密码摘要
    private fun getPasswordDigest(nonce: String,date: String, password: String): String {
        //官方计算公式  Digest = B64ENCODE( SHA1( B64DECODE( Nonce ) + Date + Password ) )
        // 步骤 1: Base64 解码 Nonce
        val decodedNonce = Base64.decode(nonce, Base64.NO_WRAP)
        // 步骤 2: 拼接 Nonce + Date + Password
        val dataToHash = decodedNonce + date.toByteArray(StandardCharsets.UTF_8) + password.toByteArray(StandardCharsets.UTF_8)
        // 步骤 3: 使用 SHA1 算法进行哈希计算
        val sha1Digest = MessageDigest.getInstance("SHA-1")
        val hashedBytes = sha1Digest.digest(dataToHash)
        // 步骤 4: 将哈希结果进行 Base64 编码
        val passwordDigest = Base64.encodeToString(hashedBytes, Base64.NO_WRAP)
        return passwordDigest
    }

    //判断字符串中是否含有IPv4的地址
    private fun extractIPv4(address: String): String {
        // IPv4 地址正则表达式，用于匹配任意位置的 IPv4 地址
        val ipv4Regex = """(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)""".toRegex()
        // 查找字符串中的 IPv4 地址并返回匹配结果，若没有找到则返回空字符串
        return ipv4Regex.find(address)?.value ?: ""
    }



    //采用读文件的方式来生成发送的数据
    private fun getSendMsg(fileName: String, replacements: List<String>):String{
        val assetManager: AssetManager = context.assets
        var inputStream: InputStream? = null
        var content = ""

        try {
            // 获取文件的输入流
            inputStream = assetManager.open(fileName)

            // 使用 BufferedReader 读取文件内容
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()

            // 逐行读取文件内容
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
            // 获取文件内容
            content = stringBuilder.toString()

            // 替换文件中的 %s 占位符
            for (replacement in replacements) {
                content = content.replaceFirst("%s", replacement)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // 关闭输入流
            inputStream?.close()
        }
        return content
    }


    suspend  fun sendPostRequest(urlString: String, postData: String):String {
        try {

            if (urlString == "" || postData == "") return  ""
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                connectTimeout = 1000  // 设置连接超时
                readTimeout = 1000     // 设置读取超时
                doOutput = true         // 允许输出
            }

            // 发送POST数据
            connection.outputStream.write(postData.toByteArray())

            // 获取服务器响应码
            val responseCode = connection.responseCode
            when(responseCode){
                HttpURLConnection.HTTP_OK -> {
//                        val contentLength = connection.contentLength
                    // 获取服务器响应输入流
                    val inputStream: InputStream = connection.inputStream
                    val bufferedInputStream = BufferedInputStream(inputStream)

                    // 使用ByteArrayOutputStream来拼接返回数据
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(2048) // 每次读取2KB
                    var bytesRead: Int

                    // 逐块读取响应数据直到读取完流的结尾
                    while (true) {
                        bytesRead = bufferedInputStream.read(buffer)
                        if (bytesRead == -1) break // 如果没有更多数据，退出循环
                        byteArrayOutputStream.write(buffer, 0, bytesRead)
                    }

                    // 获取最终的字节数据
                    val byteArray = byteArrayOutputStream.toByteArray()

                    // 将字节数组转换为字符串
                    val responseString = String(byteArray, Charset.forName("UTF-8"))

                    // 关闭连接
                    bufferedInputStream.close()
                    byteArrayOutputStream.close()
                    // 返回请求结果
                    return responseString

                }

                401 -> {
                    //401 ，权限问题   打开web
                    val err = ReturnOperation("WebView",extractIPv4(urlString),mapOf("onvif" to ""))
                    withContext(Dispatchers.Main) {
                        _request.value = err
                    }

                }
                else -> {
                    //其他的错误
                    val err = ReturnOperation("ResponseCode","$responseCode",mapOf("onvif" to ""))
                    withContext(Dispatchers.Main) {
                        _request.value = err
                    }
                }
            }


            connection.disconnect()

        } catch (e: Exception) {
            // 捕获异常并打印日志
            Log.e("sendPostRequest", "Error: ${e.message}", e)
        }
        return ""
    }

    // XML 解析函数，传入标签名列表，返回对应的数据   只解析关心的标签名   1查询标签列表     2查询属性列表 可空   暂时不支持模糊匹配
    private fun parseXml(
        xmlString: String,
        targetElementNames: List<String>,
        targetAttributes: List<String>? = null
    ): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()

        // 初始化每个目标标签的空列表
        targetElementNames.forEach { result[it] = mutableListOf() }

        // 如果 targetAttributes 不为空，初始化每个属性的空列表
        targetAttributes?.forEach { result[it] = mutableListOf() }

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentElementName = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        // 如果是目标标签，处理其属性和文本
                        if (targetElementNames.contains(tagName)) {
                            currentElementName = tagName
                        }

                        // 处理目标属性（如果 targetAttributes 不为空）:
                        // 仅在目标标签下才会处理属性
                        if (currentElementName.isNotEmpty()) {
                            targetAttributes?.forEach { attributeName ->
                                val attributeValue = parser.getAttributeValue(null, attributeName)
                                if (attributeValue != null && tagName in targetElementNames) {
                                    // 只有当标签是目标标签时才解析其属性
                                    result[attributeName]?.add(attributeValue)
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        // 如果是目标标签的文本节点，处理文本内容
                        if (currentElementName.isNotEmpty()) {
                            val text = parser.text.trim()
                            if (text.isNotEmpty()) {
                                // 直接按空格拆分标签内容（即使没有空格）
                                val valueList = text.split(" ").filter { it.isNotBlank() }  // 拆分并过滤空字符串
                                result[currentElementName]?.addAll(valueList)  // 添加拆分后的值
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        // 清空当前标签名称
                        if (targetElementNames.contains(tagName)) {
                            currentElementName = ""
                        }
                    }
                }

                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }






}