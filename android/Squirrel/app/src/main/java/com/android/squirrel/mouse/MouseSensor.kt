package com.android.squirrel.mouse


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.android.squirrel.MainActivity
import com.android.squirrel.R
import com.android.squirrel.service.SendServices
import com.android.squirrel.tools.GlobalVariable
import com.android.squirrel.tools.SharedDataPermanently
import org.rajawali3d.view.ISurface
import java.lang.Math.toDegrees
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.rajawali3d.math.Quaternion
import org.rajawali3d.view.TextureView
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.abs
import kotlin.math.floor



class MouseSensor : Fragment()
    , SensorEventListener {
    private var sendService: SendServices? = null//服务
    private var tableNameMessage = ""

    private val sensorDataQueue: LinkedBlockingQueue<Pair<Int,Pair<FloatArray,Long>>> = LinkedBlockingQueue() // 传感器数据队列
    private lateinit var sensorDataJob: Job

    //传感器
    private lateinit var sensorManager: SensorManager
    private  var rotate: Sensor? = null
    private  var gyroscope: Sensor? = null

    private  var viewPager: ViewPager2? = null //左右滑动回调
    //3D绘图
    private lateinit var surface: TextureView
    private lateinit var mouseRenderer: MouseRenderer

    private val reportDescriptor = ByteArray(5)//发送报告符数据内容
    //图表
    private lateinit var lineChart: LineChart
    private val lineChartX = mutableListOf<Entry>()
    private val lineChartY = mutableListOf<Entry>()
    private val lineChartZ = mutableListOf<Entry>()
    private lateinit var dataSetX: LineDataSet
    private lateinit var dataSetY: LineDataSet
    private lateinit var dataSetZ: LineDataSet

    private var pointer = 1
    private var movingDistance = 50 //翻页移动距离
    private var xAxis = 2
    private var yAxis = 0
    private var xBackward = true
    private var yBackward = true
    private var gyroscopeThreshold = 0.1f    //陀螺仪积分阀值
    private val moveWheel = IntArray(2) //滚轮累加
    private lateinit var gestureDetector: GestureDetector

    private var touchDown = false //触摸按下时不做运动解析

    private var toggleSwitch = true //指针与滚轮转换

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.mouse_sensor, container, false)

        surface = view.findViewById(R.id.rajwali_surface)
        surface.setFrameRate(60.0)//回定帧率(默认约60)
        surface.setRenderMode(ISurface.RENDERMODE_WHEN_DIRTY)
        mouseRenderer = MouseRenderer(requireContext())
        surface.setSurfaceRenderer(mouseRenderer)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sensorDataStatus()
        initSensor()
        GlobalVariable.viewPager.observe(viewLifecycleOwner) { data ->
            viewPager = data
        }
        GlobalVariable.serviceLiveData.observe(viewLifecycleOwner) { service ->
            sendService = service
        }

        viewPager = (activity as MainActivity).findViewById(R.id.main_view_pager)
        viewPager!!.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                // 页面正在被用户拖动完成后
                if (state == ViewPager2.SCROLL_STATE_SETTLING){
                    touchDown = false
                    lineChart.setBackgroundColor(Color.BLACK)// 设置整体背景颜色
                    moveWheel.fill(0)

                }
            }
        })


        lineChart = view.findViewById(R.id.mouse_sensor_lineChart)//图表
        setLineChartAppearance(lineChart,getString(R.string.pointer))


        // 初始化手势检测器
        gestureDetector = GestureDetector(requireActivity(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 处理双击事件
                toggleSwitch = !toggleSwitch
                when(toggleSwitch){
                    true ->  setLineChartAppearance(lineChart,getString(R.string.pointer))
                    false -> setLineChartAppearance(lineChart,getString(R.string.wheel))
                }
                return true
            }
        })


        val touchListener = View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    touchDown = false
                    lineChart.setBackgroundColor(Color.BLACK)// 设置整体背景颜色
                }
                MotionEvent.ACTION_DOWN -> {
                    touchDown = true
                    lineChart.setBackgroundColor(Color.WHITE)// 设置整体背景颜色
                }
            }
            gestureDetector.onTouchEvent(event) // 将事件传递给手势检测器
            v.performClick() // 确保执行点击事件
            true
        }

        surface.setOnTouchListener(touchListener)
        lineChart.setOnTouchListener(touchListener)

        //左 右 按键注册
        setupButton(view,R.id.mouse_sensor_left, 0)
        setupButton(view,R.id.mouse_sensor_right, 1)
    }
    private fun setupButton(view: View,buttonId: Int, bitIndex: Int) {
        val button = view.findViewById<Button>(buttonId)
        button.setOnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                    reportDescriptor[0] = reportDescriptor[0].toggleBit(bitIndex)
                    sendData()
                }
            }
            false
        }
    }
    override fun onResume() {
        super.onResume()
        surface.onResume()
        mouseRenderer.onResume()
        mouseRenderer.modeVisible()
        //取参数
        val parameter = SharedDataPermanently("parameter",requireContext())
        pointer = parameter.getParameter("pointer",pointer) as Int
        movingDistance = parameter.getParameter("MovingDistance",movingDistance) as Int
        gyroscopeThreshold = parameter.getParameter("gyroscopeThreshold",gyroscopeThreshold) as Float

        xAxis = parameter.getParameter("xMapping",xAxis) as Int
        yAxis = parameter.getParameter("yMapping",yAxis) as Int
        xBackward = parameter.getParameter("xBackward",false) as Boolean
        yBackward = parameter.getParameter("yBackward",false) as Boolean
        listenerSensor()

        GlobalVariable.macroStatus.observe(requireActivity()) { status ->
            val (success, message) = status
            tableNameMessage = message
        }


    }



    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)// 注销传感器监听器
        surface.onPause()
        mouseRenderer.onPause()
        mouseRenderer.removeModel()


    }

    /**消毁*/
    override fun onDestroy() {
        super.onDestroy()
        sensorDataJob.cancel()
    }

    private fun initSensor(){
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager //初始化传感器管理器
        rotate = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) //姿态
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) //陀螺仪

    }

    // 注册传感器监听器
    private fun listenerSensor(){
        rotate?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {} //传感器数据的精度变化时

    //传感器数据变化时
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> sensorDataQueue.put(Pair(Sensor.TYPE_ROTATION_VECTOR, Pair(event.values,event.timestamp)))
            Sensor.TYPE_GYROSCOPE -> sensorDataQueue.put(Pair(Sensor.TYPE_GYROSCOPE, Pair(event.values,event.timestamp)))
        }
    }

    // 用于记录实体上下按键的状态
    private val keyStateMap = mutableMapOf(
        KeyEvent.KEYCODE_VOLUME_UP to false,
        KeyEvent.KEYCODE_VOLUME_DOWN to false
    )

    // 处理音量键按下事件
    fun handleVolumeKeyPress(keyCode: Int) {
        if (keyStateMap[keyCode] == false) { // 检查是否已经处理过按下事件
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    reportDescriptor[0] = reportDescriptor[0].toggleBit(0)
                    sendData()
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    reportDescriptor[0] = reportDescriptor[0].toggleBit(1)
                    sendData()
                }
            }
            keyStateMap[keyCode] = true // 更新状态为已按下
        }
    }

    // 处理音量键抬起事件
    fun handleVolumeKeyRelease(keyCode: Int) {
        if (keyStateMap[keyCode] == true) { // 检查是否已经处理过抬起事件
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    reportDescriptor[0] = reportDescriptor[0].toggleBit(0)
                    sendData()
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    reportDescriptor[0] = reportDescriptor[0].toggleBit(1)
                    sendData()
                }
            }
            keyStateMap[keyCode] = false // 更新状态为未按下
        }
    }

    private fun Byte.toggleBit(bitIndex: Int): Byte {
        require(bitIndex in 0..7) { "bitIndex must be between 0 and 7" }
        val mask = 1 shl bitIndex
        return (this.toInt() xor mask).toByte()
    }


    // 设置图表风格
    private fun setLineChartAppearance(lineChart: LineChart, description: String) {
        // 设置整体背景颜色（可选）
        // lineChart.setBackgroundColor(Color.BLACK)

        // 图例配置
        val legend = lineChart.legend
        legend.textColor = Color.WHITE // 设置图例字体颜色
        legend.isEnabled = true // 确保图例可见

        // Y 轴配置
        lineChart.axisLeft.textColor = Color.WHITE // 设置左侧 Y 轴标签颜色
        lineChart.axisLeft.setLabelCount(10, true) // 设置 Y 轴标签显示数量
        lineChart.axisRight.isEnabled = false // 隐藏右侧 Y 轴标签

        // X 轴配置
        lineChart.xAxis.isEnabled = false // 显示 X 轴标签
        lineChart.xAxis.textColor = Color.WHITE // 设置 X 轴标签颜色
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM // 设置 X 轴标签位置
        lineChart.xAxis.setLabelCount(10, true) // 设置 X 轴标签显示数量

        // 描述信息配置
        lineChart.description.text = description // 设置图表描述
        lineChart.description.textColor = Color.YELLOW // 描述文本颜色
        lineChart.description.textSize = 16f // 描述文本大小

        // 高亮和交互配置
        lineChart.isHighlightPerTapEnabled = false // 点击高亮禁用
        lineChart.isHighlightPerDragEnabled = false // 拖动高亮禁用
        lineChart.setDragEnabled(true) // 启用图表拖动
        lineChart.setScaleEnabled(false) // 缩放
        lineChart.setPinchZoom(false) // 双指缩放
        lineChart.isDoubleTapToZoomEnabled = false // 禁用双击缩放

        // 折线图风格设置
        dataSetX = LineDataSet(lineChartX, getString(R.string.xAxis)).apply {
            color = Color.RED
            valueTextColor = Color.RED
            mode = LineDataSet.Mode.CUBIC_BEZIER // 设置为平滑的贝塞尔曲线
            setDrawValues(false) // 禁用每个数据点数值显示
            setDrawCircles(false) // 禁用数据点圆圈绘制

        }

        dataSetY = LineDataSet(lineChartY, getString(R.string.yAxis)).apply {
            color = Color.GREEN
            valueTextColor = Color.GREEN
            mode = LineDataSet.Mode.CUBIC_BEZIER // 设置为平滑的贝塞尔曲线
            setDrawValues(false) // 禁用每个数据点数值显示
            setDrawCircles(false) // 禁用数据点圆圈绘制
        }

        dataSetZ = LineDataSet(lineChartZ, getString(R.string.zAxis)).apply {
            color = Color.BLUE
            valueTextColor = Color.BLUE
            mode = LineDataSet.Mode.CUBIC_BEZIER // 设置为平滑的贝塞尔曲线
            setDrawValues(false) // 禁用每个数据点数值显示
            setDrawCircles(false) // 禁用数据点圆圈绘制

        }

        // 设置图表数据
        lineChart.data = LineData(dataSetX, dataSetY, dataSetZ)
    }


    //更新图表数据 (效率太低了)
    private fun upDateChart(newEntries: List<FloatArray>) {
        val size = 500 //图表数据显示的大小
        lineChart.post {
            val lineData = lineChart.data
            val chartDataSets = lineData.dataSets
//                if (chartDataSets.isEmpty())
            // 保持每个数据集不超过 listDataSize 个数据点
            for (dataSet in listOf(lineChartX, lineChartY,lineChartZ)) {
                if (dataSet.size > size) {
                    // 数据前移
                    for (entry in dataSet) {
                        entry.x -= 1
                    }
                    dataSet.removeAt(0) // 删除最旧的条目
                }
            }

            // 新增数据
            newEntries.forEachIndexed { index, entries ->
                if (index < chartDataSets.size) {
                    val dataSet = chartDataSets[index]
                    if (dataSet is LineDataSet) {
                        dataSet.addEntry(Entry(dataSet.entryCount.toFloat(), entries[0]))
                    }
                }
            }

            var maxYValue = 0f
            for (dataSet in chartDataSets) {
                val entries = (dataSet as? LineDataSet)?.values ?: continue
                for (entry in entries) {
                    maxYValue = maxOf(maxYValue, entry.y)
                }
            }

            var minYValue = 0f
            for (dataSet in chartDataSets) {
                val entries = (dataSet as? LineDataSet)?.values ?: continue
                for (entry in entries) {
                    minYValue = minOf(minYValue, entry.y)
                }
            }
            // 设置 Y 轴的显示范围
            lineChart.axisLeft.axisMaximum = maxYValue
            lineChart.axisLeft.axisMinimum = minYValue

            // 移动视图到最新的数据点
            lineChart.moveViewToX(chartDataSets[0].entryCount.toFloat())

            // 刷新图表
            lineData.notifyDataChanged() // 通知数据已更改
            lineChart.notifyDataSetChanged() // 更新图表数据
            lineChart.invalidate() // 刷新图表显示

        }
    }


    private fun sensorDataStatus(){
        var gyroscopeTimestamp = 0L
        val accumulatedAngle =  DoubleArray(3)//当前旋转角度数据的累加取整后乘于的小数部分
        val lowPassFilter = FloatArray(3)     //低通（平滑数据）
        val alpha = 0.8f// 平滑系数，调整此值以平衡响应速度和准确性  滤波器的平滑因子
        sensorDataJob = CoroutineScope(Dispatchers.IO).launch {
//        withContext(Dispatchers.Main) {}// 更换到Main线程
            while (isActive) {
                try {
                    val sensorData = sensorDataQueue.take() // 阻塞队列直到有数据可用
                    val sensorType = sensorData.first   //取传感器类型
                    val (values, timestamp) = sensorData.second //取传感器数据
                    // 处理传感器数据
                    when (sensorType) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            // 创建一个包含4个元素的四元数数组
                            val quaternionValues = FloatArray(4)
                            // 将 rotation vector 转换为四元数
                            SensorManager.getQuaternionFromVector(quaternionValues, values)
                            val quaternion = Quaternion(
                                quaternionValues[0].toDouble(),
                                quaternionValues[1].toDouble(),
                                -quaternionValues[3].toDouble(),
                                -quaternionValues[2].toDouble()
                            )
                            mouseRenderer.setRaptorValues(quaternion) //飞机位置四元数
                        }
                        Sensor.TYPE_GYROSCOPE -> {
                            if (gyroscopeTimestamp == 0L)  gyroscopeTimestamp = timestamp
                            val deltaTime = (timestamp - gyroscopeTimestamp) / 1_000_000_000.0//ns转为秒
                            for (i in 0..2) {
                                lowPassFilter[i] = alpha * lowPassFilter[i] + (1 - alpha) * values[i]
                            }

                            if (abs(lowPassFilter[0]) > gyroscopeThreshold){
                                accumulatedAngle[0] += toDegrees(lowPassFilter[0] * deltaTime) * 60
                            }else accumulatedAngle[0] = 0.0
                            if (abs(lowPassFilter[1]) > gyroscopeThreshold){
                                accumulatedAngle[1] += toDegrees(lowPassFilter[1] * deltaTime) * 60
                            }else accumulatedAngle[1] = 0.0
                            if (abs(lowPassFilter[2]) > gyroscopeThreshold){
                                accumulatedAngle[2] += toDegrees(lowPassFilter[2] * deltaTime) * 60
                            }else accumulatedAngle[2] = 0.0


                            // 获取整数部分
                            val x = floor(accumulatedAngle[0])
                            val y = floor(accumulatedAngle[1])
                            val z = floor(accumulatedAngle[2])

                            if(abs(x) > 1 || abs(y) > 1 || abs(z) > 1 ){
                                accumulatedAngle[0] -= x
                                accumulatedAngle[1] -= y
                                accumulatedAngle[2] -= z

                                var shenX = 0.0
                                when(xAxis){
                                    0-> shenX = x
                                    1-> shenX = y
                                    2-> shenX = z
                                }
                                if (xBackward) shenX = -shenX
                                var sendY = 0.0
                                when(yAxis){
                                    0-> sendY = x
                                    1-> sendY = y
                                    2-> sendY = z
                                }
                                if (yBackward) sendY = -sendY
                                mouseDataSend(shenX.toInt(), sendY.toInt())
                            }

                            upDateChart(
                                listOf(
                                    floatArrayOf(lowPassFilter[0]),
                                    floatArrayOf(lowPassFilter[1]),
                                    floatArrayOf(lowPassFilter[2])
                                )
                            )
                            gyroscopeTimestamp = timestamp
                        }

                    }
                } catch (e: InterruptedException) {
                    // 处理线程中断
                    break
                }
            }
        }
    }



    private fun mouseDataSend(moveX:Int,moveY:Int){
        if(!touchDown) {
            var rotationalX = 0
            var rotationalY = 0
            when(pointer){
                0 -> {
                    rotationalX = moveX
                    rotationalY = moveY
                }
                in 1..10-> {
                    rotationalX = BigDecimal(moveX * pointer).setScale(0, RoundingMode.HALF_UP).toInt()
                    rotationalY = BigDecimal(moveY * pointer).setScale(0, RoundingMode.HALF_UP).toInt()
                }
                else -> {
                    rotationalX = BigDecimal(moveX / abs(pointer)).setScale(0, RoundingMode.HALF_UP).toInt()
                    rotationalY = BigDecimal(moveY / abs(pointer)).setScale(0, RoundingMode.HALF_UP).toInt()
                }
            }

            when(toggleSwitch){
                true -> {
                    // 指针
                    reportDescriptor[1] = rotationalX.toByte()
                    reportDescriptor[2] = rotationalY.toByte()
                    sendData()
                }
                false -> {
                    moveWheel[0] += rotationalX
                    moveWheel[1] += rotationalY
                    if (abs(moveWheel[0]) > movingDistance){
                        //左右滚动
                        if (moveWheel[0] > 0){
                            reportDescriptor[4] = 1
                            sendData()
                        }else {
                            reportDescriptor[4] = -1
                            sendData()
                        }
                        moveWheel.fill(0)
                    }
                    else if (abs(moveWheel[1]) > movingDistance){
                        if (moveWheel[1] > 0){
                            reportDescriptor[3] = -1
                            sendData()
                        }else {
                            reportDescriptor[3] = 1
                            sendData()
                        }
                        moveWheel.fill(0)
                    }



                }
            }

            reportDescriptor[1] = 0
            reportDescriptor[2] = 0
            reportDescriptor[3] = 0
            reportDescriptor[4] = 0
        }
    }

    private fun sendData(){
        sendService?.setReporterData(reportDescriptor,tableNameMessage) //提交数据
    }

}


