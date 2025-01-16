package com.android.squirrel.ipc

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

import com.android.squirrel.R
import com.android.squirrel.ipc.onvif.DeviceDisplayInformation
import com.android.squirrel.ipc.onvif.OnvifRequests
import com.android.squirrel.ipc.onvif.PtzData
import com.android.squirrel.ipc.video.PlayVideo
import com.android.squirrel.tools.GlobalVariable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class SurveillanceCamera: Fragment()
    , CameraAdapter.OnGestureEventListener
{
    private lateinit var playVideo: PlayVideo
    private lateinit var onvif: OnvifRequests
    private lateinit var playerView: TextureView  //显示视频

    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val onScrollThreshold = 10  // 设置一个阈值，只有当滑动超过该距离时才会处理
    private val onScaleThreshold = 0.05f // 设置一个阈值，控制缩放灵敏度

    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var constraintSet: ConstraintSet
    private lateinit var deviceList: RecyclerView

    private var degrees = 0f  //当前屏幕的角度

    private var textureViewWidth = 0
    private var textureViewHeight = 0
    private  var viewPager: ViewPager2? = null
    private var displayMap: MutableMap<String, DeviceDisplayInformation> = mutableMapOf() //显示标签
    private lateinit var cameraAdapter: CameraAdapter

    private lateinit var webView :WebView
    private var webDisplay = false  //web 显示的状态
    private lateinit var hideWeb :ImageButton
    private lateinit var scanPorts :ImageButton
    private lateinit var playUrl :ImageButton
    private var playDeviceName = mapOf("" to "") //那个设备在播放

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.ipc_surveillance_camera, container, false)

        GlobalVariable.viewPager.observe(viewLifecycleOwner) { data ->
            viewPager = data
        }


        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 初始化 TextureView
        playerView = view.findViewById(R.id.ipc_view)

        textureViewWidth = playerView.width
        textureViewHeight = playerView.height

        constraintLayout = view.findViewById(R.id.constraintLayout)
        constraintLayout.post {
            // 获取 ConstraintLayout 的宽度和高度
            textureViewWidth = constraintLayout.width
            textureViewHeight = constraintLayout.height
        }
        // 复制原始约束
        constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        cameraAdapter = CameraAdapter(displayMap, this)
        deviceList = view.findViewById(R.id.ipc_last)

        val gridLayoutManager = GridLayoutManager(requireContext(), 2) // 设置每行显示的标签数量
        deviceList.layoutManager = gridLayoutManager
        deviceList.adapter = cameraAdapter

        // 创建一个 ScaleGestureDetector 来处理双指缩放
        scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // 获取缩放因子
                val scaleFactor = detector.scaleFactor
                // 计算缩放因子的变化量
                val scaleDifference = scaleFactor - 1.0f
                // 判断缩放因子的变化量是否大于阈值
                if (Math.abs(scaleDifference) > onScaleThreshold) {
                    val ptzData = PtzData("0","0","0")
                    if (scaleFactor > 1) {
//                        println("放大了")
                        ptzData.zoom = "1"
                    } else {
//                        println("缩小了")
                        ptzData.zoom = "-1"
                    }
                    for ((key, value) in playDeviceName) {
                        when(key){
                            "onvif" -> onvif.setPtz(value,ptzData)
                        }
                    }
                }
                return true
            }
        })

        // 来处理单指滑动
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {

                if (Math.abs(distanceX) > onScrollThreshold || Math.abs(distanceY) > onScrollThreshold) {
                    val ptzData = PtzData("0","0","0")
                    // 判断 X 和 Y 的滑动量，选择更大的一方
                    if (Math.abs(distanceX) > Math.abs(distanceY)) {
                        // 如果水平方向的滑动量更大，认为是横向滑动
                        when {
                            distanceX > 0 -> {
//                                println("滑动方向：左")
                                ptzData.x = "1"
                            }
                            distanceX < 0 -> {
                                ptzData.x = "-1"
//                                println("滑动方向：右")
                            }
                        }
                    } else {
                        // 如果垂直方向的滑动量更大，认为是纵向滑动
                        when {
                            distanceY > 0 -> {
//                                println("滑动方向：上")
                                ptzData.y= "1"
                            }
                            distanceY < 0 -> {
                                ptzData.y= "-1"
//                                println("滑动方向：下")
                            }
                        }
                    }

                    for ((key, value) in playDeviceName) {
                        when(key){
                            "onvif" -> onvif.setPtz(value,ptzData)
                        }
                    }

                }


                return true
            }


        })


        playerView.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event) // 处理缩放
            gestureDetector.onTouchEvent(event)// 处理滑动

            true
        }


        // 初始化 PlayVideo
        playVideo = PlayVideo(requireActivity(), playerView)
        //屏幕方向发生了变化 调整显示
        playVideo.degrees.observe(viewLifecycleOwner, Observer {
            rotateTextureView(it)
        })
        webView = view.findViewById(R.id.ipc_web_view)
        webView.visibility = View.GONE //完全隐藏
        // 获取 WebSettings 对象
        val webSettings: WebSettings = webView.settings
        // 启用 JavaScript
        webSettings.javaScriptEnabled = true  //启用 JavaScript（如果需要）

        // 启用缩放支持
        webSettings.setSupportZoom(true)
        // 启用双指缩放
        webSettings.builtInZoomControls = true
        // 启用自适应屏幕宽度
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true



        // 启用硬件加速（API Level 19 以上才有效）
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
//        } else {
//            // 低版本设备上使用软渲染
//            webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
//        }
//
//// 启用 DOM 存储
//        webSettings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            // 重定向时会调用此方法
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // 返回 false 表示 WebView 将继续加载此 URL
                return super.shouldOverrideUrlLoading(view, request)
            }

            // 页面加载错误时调用
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                val url = request.url.toString()
                if (url.startsWith("https://")) {
                    val httpUrl = url.replace("https://", "http://")
                    webView.loadUrl(httpUrl)  // 使用 http 协议重新加载
                }
            }
        }



        hideWeb = view.findViewById(R.id.ipc_hideWeb)
        hideWeb.setOnClickListener{
            viewPager?.setUserInputEnabled(true)
            webDisplay = false
            webView.visibility = View.GONE //完全隐藏
        }
        scanPorts = view.findViewById(R.id.ipc_scanPorts)
        scanPorts.setOnClickListener{
            scanPorts()
        }
        playUrl = view.findViewById(R.id.ipc_playUrl)
        playUrl.setOnClickListener{
            playUrl()
        }


    }


    override fun onResume() {
        super.onResume()
//        viewPager?.setUserInputEnabled(false)
        onvif = OnvifRequests(requireActivity())
        //ws 发现设备后更新标签显示
        onvif.display.observe(viewLifecycleOwner, Observer {
            //找到了设备 ，标签列表的背景修改为黑色
            deviceList.setBackgroundResource(R.color.black)
            //更新显示的数据
            displayMap.putAll(it)
            //更新显示
            cameraAdapter.updateDeviceData(displayMap)

        })


        onvif.request.observe(viewLifecycleOwner, Observer {
            when(it.command){
                "ResponseCode" -> Log.d("out", it.data)
                "WebView" -> {
                    webDisplay = true
                    webView.visibility = View.VISIBLE //显示
                    webView.loadUrl("http://${it.data}")
                    viewPager?.setUserInputEnabled(false)
                }
                "PlayVideo" -> {
                    playVideo.playUri(it.data)
                    playDeviceName = it.playName
                }
            }

        })


    }

    override fun onPause() {
        super.onPause()
//        viewPager?.setUserInputEnabled(true)
        onvif.stop()
        displayMap.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playVideo.unInit() // 确保销毁时清理资源

    }






    // 标签实现回调方法
    override fun onGestureEvent(position: Int, eventType:String) {
        // 检查索引是否有效
        if (position in 0 until displayMap.size) {
            val deviceList = displayMap.keys.toList()
            val key = deviceList[position] //获得存入map中的名称
            val msg = displayMap[key] //获取设备的全部信息

            when(eventType){
                "single tapped" ->{
                    //单击播放设备视频
//                    playUrl("rtsp://admin:abcd1234@192.168.1.64:554/Streaming/Channels/101?transportmode=unicast&profile=Profile_1")

                    if (msg != null) {
                        //设备是个什么类型的  暂时只有onvif
                        when(msg.deviceType){
                            "onvif" ->{
                                val uri = onvif.getPlayUri(key)
                                if (uri == "") {
                                    setLogin("onvif", key)//提示输入用户名与密码
                                }else {
                                    playVideo.playUri(uri)
                                    playDeviceName =  mapOf("onvif" to key)
                                }

                            }
                        }
                    }

                }
                "double tapped" ->{
                    //双击为设置一些参数
                    if (msg != null) {
                        //设备是个什么类型的  暂时只有onvif
                        when(msg.deviceType){
                            "onvif" ->{
                                setCamera("onvif", key)
                            }
                        }
                    }

                }
                "long pressed" ->{
                    //长按为调用 WebView
                    msg?.deviceIp?.let {
                        webDisplay = true
                        webView.visibility = View.VISIBLE //显示
                        webView.loadUrl("http://$it")
                        viewPager?.setUserInputEnabled(false)

                    }
                }

            }

        }

    }

    //视频显示的方向
    private fun rotateTextureView(data: FloatArray) {
        //当web 显示的时候不接受方向转换
        if (degrees == data[0] || webDisplay) return
        degrees = data[0]
        // 清除原有约束
        constraintSet.clear(playerView.id)
        // 根据屏幕方向设置不同的约束
        when (degrees) {
            90f, 270f -> {
                // 横屏时，设置新的约束：TextureView 占据整个屏幕
                constraintSet.connect(playerView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                constraintSet.connect(playerView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                constraintSet.connect(playerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(playerView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                // 横屏时，宽度和高度设置为自动计算（MATCH_PARENT），确保填充屏幕
                constraintSet.constrainWidth(playerView.id, ConstraintLayout.LayoutParams.MATCH_PARENT)
                constraintSet.constrainHeight(playerView.id, ConstraintLayout.LayoutParams.MATCH_PARENT)
            }
            0f, 180f -> {
                // 竖屏时，使用与竖屏相关的约束：TextureView 被限定在特定区域内（ipc_windows）
                constraintSet.connect(playerView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                constraintSet.connect(playerView.id, ConstraintSet.BOTTOM, R.id.ipc_windows, ConstraintSet.BOTTOM)
                constraintSet.connect(playerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(playerView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                // 竖屏时，设置宽高为 MATCH_PARENT 和相对比例
                constraintSet.constrainWidth(playerView.id, 0)
                constraintSet.constrainHeight(playerView.id, 0)
            }
        }
        // 应用新的约束到 ConstraintLayout
        constraintSet.applyTo(constraintLayout)
        playerView.rotation = degrees

        playerView.requestLayout()  // 强制更新布局

        // 根据旋转方向调整控件尺寸
        val layoutParams = playerView.layoutParams as ConstraintLayout.LayoutParams
        when (degrees) {
            90f, 270f -> {
                layoutParams.dimensionRatio = null
                // 横屏时，调整控件尺寸
                layoutParams.width = textureViewHeight
                layoutParams.height = textureViewWidth
                viewPager?.setUserInputEnabled(false)
            }
            0f, 180f -> {
                val aspectRatio = data[1]
                layoutParams.dimensionRatio = "$aspectRatio:1"  // 设置视频的宽高比（保持比例）
                viewPager?.setUserInputEnabled(true)
            }
        }

        // 更新布局参数
        playerView.layoutParams = layoutParams

        //web 没有显示就隐藏
        if (!webDisplay){
            webView.visibility = View.GONE //完全隐藏
        }

    }

    private fun setLogin(type:String,key:String){
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.ipc_login, null)
        //引入相关的控件资源ID
        val positive: Button = view.findViewById(R.id.ipc_execute)
        val nameText: EditText = view.findViewById(R.id.ipc_username)
        val passwordText: EditText = view.findViewById(R.id.ipc_password)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
//            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.black) //设置背景
        dialog.show()
        nameText.postDelayed({
            nameText.requestFocus() //获取焦点
            //需要键盘自动弹出
            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(nameText, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
        positive.setOnClickListener {
            val name = nameText.text.toString()
            val password = passwordText.text.toString()
            // 获取用户名和密码
            if (name.isNotEmpty() && password.isNotEmpty() ) {
                when(type){
                    "onvif" -> {
                        onvif.getUri(name,password,key)
                    }
                }
                dialog.dismiss()
            }
        }
    }

    private fun setCamera(type:String,key:String){
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.ipc_settings, null)
        val deviceTime :TextView =  view.findViewById(R.id.deviceTime)
        val mobileTime :TextView =  view.findViewById(R.id.mobileTime)
        val timeSync: Button = view.findViewById(R.id.timeSync)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.black) //设置背景
        dialog.show()
        when(type){
            "onvif" -> {
                onvif.checkDeviceTime(key) { time ->
                    deviceTime.text = time
                }
            }
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        mobileTime.text = dateFormat.format(Date())


        timeSync.setOnClickListener {
            when(type){
                "onvif" -> {
                    onvif.timeSync(key) { time ->
                        if (time == "yes"){
                            onvif.checkDeviceTime(key) { time ->
                                deviceTime.text = time
                            }
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                            mobileTime.text = dateFormat.format(Date())
                        }
                    }
                }
            }

        }

    }

    private fun scanPorts(){
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.ipc_scan_ports, null)
        val msg :TextView =  view.findViewById(R.id.ipc_scan_ports_msg)
        val cancel: Button = view.findViewById(R.id.ipc_scan_ports_cancel)
        var job: Job? = null
        val batchSize = 10
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.black) //设置背景
        dialog.show()

        val ipAddress = AvailableIPAddresses()

        //获取本地子网全部可能存在的ip地址
        var ipList = ipAddress.getAvailableIPAddresses(requireContext())


        if (ipList.isNotEmpty()){

            // 使用协程在 IO 线程池中执行
            job = CoroutineScope(Dispatchers.IO).launch {
                val remainingIPs = ipList.toMutableList() // 不修改原始列表
                var currentIndex = 0 // 初始化当前位置计数器
                // 每次取 batchSize 个 IP 进行处理
                while (remainingIPs.isNotEmpty() && isActive) {
                    val batch = remainingIPs.take(batchSize)
                    remainingIPs.removeAll(batch)

                    withContext(Dispatchers.Main) {
                        msg.text = "$currentIndex   ->>>--->>>-    ${ipList.size}"
                    }
                    // 并行扫描当前批次的 IP
                    val deferredResults = batch.map { ip ->
                        async {
                            for (port in listOf(80, 443)) {
                                if (isPortOpen(ip, port)) {
                                    withContext(Dispatchers.Main) {
                                        val newDisplay: Map<String, DeviceDisplayInformation> =  mapOf(
                                            ip to DeviceDisplayInformation(ip, "",""))
                                        deviceList.setBackgroundResource(R.color.black)
                                        displayMap.putAll(newDisplay) //更新显示的数据
                                        cameraAdapter.updateDeviceData(displayMap) //更新显示
                                    }
                                }
                            }
                        }
                    }

                    // 等待所有当前批次的任务完成
                    deferredResults.awaitAll()
                    // 更新当前位置
                    currentIndex += batch.size
                }

                job?.cancel()
                dialog.dismiss() // 关闭对话框
            }


        }else dialog.dismiss() // 关闭对话框



        cancel.setOnClickListener {
            job?.cancel()
            dialog.dismiss() // 关闭对话框
        }

    }
    /**
     * 判断指定 IP 地址的端口是否开放
     * @param ip IP 地址
     * @param port 端口
     * @return 如果端口开放，返回 true；否则返回 false
     */
    private fun isPortOpen(ip: String, port: Int): Boolean {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 1000) // 1秒超时
            return true
        } catch (e: Exception) {
            return false
        } finally {
            socket?.close()
        }
    }

    private fun  playUrl(){
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.ipc_play_uri, null)
        val uri :EditText =  view.findViewById(R.id.ipc_urlAddrs)
        val play: ImageButton = view.findViewById(R.id.ipc_playUrl)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.black) //设置背景
        dialog.show()

        play.setOnClickListener {
            val uriAddress = uri.text.toString()
            if (uriAddress.isNotEmpty()){
                playVideo.playUri(uriAddress)
            }
            dialog.dismiss() // 关闭对话框
        }
    }

}