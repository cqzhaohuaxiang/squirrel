package com.android.squirrel.set


import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.android.squirrel.R
import com.android.squirrel.service.SendServices
import com.android.squirrel.tools.GlobalVariable
import com.android.squirrel.tools.SharedDataPermanently
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat



class OnSet : Fragment() {
    private lateinit var parameter: SharedDataPermanently
    private lateinit var sendService: SendServices//服务

    private lateinit var createButton: RadioGroup

    private lateinit var pointerSeekBar: SeekBar
    private lateinit var pointerTextView: TextView
    private var pointer = 0

    private lateinit var iconOffsetSeekBar: SeekBar
    private lateinit var iconOffsetTextView: TextView
    private var iconOffset = 200

    private lateinit var movingDistanceSeekBar: SeekBar
    private lateinit var movingDistanceTextView: TextView
    private var movingDistance = 50

    private lateinit var gyroscopeThresholdSeekBar: SeekBar
    private lateinit var gyroscopeThresholdTextView: TextView
    private var gyroscopeThreshold = 0.1f
    private val decimalFormat = DecimalFormat("#.######") //数据按照这个格式显示

    private lateinit var resetButton: Button
    private lateinit var screenLockSwitch: SwitchMaterial
    private lateinit var longKey: SwitchMaterial
    private lateinit var retransmissionSwitch: SwitchMaterial
    private lateinit var xBackward: SwitchMaterial
    private lateinit var yBackward: SwitchMaterial

    private lateinit var xMapping: RadioGroup
    private lateinit var yMapping: RadioGroup
    private var xAxis = 2
    private var yAxis = 0

    // 正则表达式：允许字母或中文字符开头，后续只能是字母、中文字符或数字
    private val tableNamePattern = "^[A-Za-z\\u4e00-\\u9fa5][A-Za-z0-9\\u4e00-\\u9fa5]*$"
    //    ^(?!.*\\s) 这部分是负向预查，它确保整个字符串中 不包含任何空白字符（空格、制表符、换行符等）。
    //    [A-Za-z\\u4e00-\\u9fa5] 表示表名必须以字母或中文字符开头。
    //    [A-Za-z0-9\\u4e00-\\u9fa5]* 表示后续字符可以是字母、数字或中文字符。
    // 常见 SQL 关键字
    private val sqlKeywords = setOf(
        "select", "from", "where", "insert", "update", "delete", "join", "into", "values", "create",
        "alter", "drop", "table", "column", "and", "or", "not", "in", "exists", "like", "group", "order"
    )
    private lateinit var macroAdd: Button
    private lateinit var macroDelete: Button
    private lateinit var macroRecord: Button
    private lateinit var macroSend: Button


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.set, container, false)



        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parameter = SharedDataPermanently("parameter", requireContext())
        GlobalVariable.serviceLiveData.observe(viewLifecycleOwner) { service ->
            sendService = service
        }


        initViews(view)
        setupSeekBars()
        //屏幕锁定的开关
        setupSwitch(screenLockSwitch, "screenLock") { isChecked ->
            if (isChecked) {
                requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // 不自动锁屏
            } else {
                requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // 锁屏
            }
        }
        setupSwitch(longKey, "longKey") {}

        setupSwitch(retransmissionSwitch, "retransmission") {}



        setupSwitch(xBackward, "xBackward") {}
        setupSwitch(yBackward, "yBackward") {}

        //恢复默认参数
        resetButton.setOnClickListener {
            pointer = 0
            iconOffset = 200
            movingDistance = 50
            gyroscopeThreshold = 0.1f
            screenLockSwitch.isChecked= false
            longKey.isChecked = false
            retransmissionSwitch.isChecked = false
            xBackward.isChecked= true
            yBackward.isChecked= true
            xAxis = 2
            yAxis = 0
            //显示
            setParameterDisplay()
            setRadioGroupDisplay()
        }
        xMapping.setOnCheckedChangeListener { _, checkedId ->
            // 通过 ID 来区分点击的是哪个 RadioButton
            when (checkedId) {
                R.id.mouse_x_axis_x -> xAxis = 0
                R.id.mouse_x_axis_y -> xAxis = 1
                R.id.mouse_x_axis_z -> xAxis = 2
            }
            parameter.setParameter("xMapping", xAxis)
        }
        yMapping.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.mouse_y_axis_x -> yAxis = 0
                R.id.mouse_y_axis_y -> yAxis = 1
                R.id.mouse_y_axis_z -> yAxis = 2
            }
            parameter.setParameter("yMapping", yAxis)
        }


        macroAdd = view.findViewById(R.id.macroAdd)
        macroAdd.setOnClickListener{
            resetRecordButton()
            showAddDialog()
        }
        macroDelete = view.findViewById(R.id.macroDelete)
        macroDelete.setOnClickListener{
            resetRecordButton()
            showDeleteDialog()
        }
        macroRecord = view.findViewById(R.id.macroRecord)
        macroRecord.setOnClickListener{
            //当点击记录按键时
            var updateUp = false
            var updateDown = false
            val getId = createButton.checkedRadioButtonId
            // 如果有按钮被选中
            if (getId != -1) {
                GlobalVariable.macroStatus.observe(requireActivity()) { status ->
                    val (success, message) = status
                    if (success){
                        updateUp = true
                    }else updateDown = true
                }

            }else {
                msgError(getString(R.string.select))
            }

            if (updateDown){
                val button: RadioButton = requireView().findViewById(getId)
                val buttonText = button.text.toString()
                GlobalVariable.setMacroStatus(true, buttonText)// 设置宏状态
                macroRecord.setBackgroundResource(R.drawable.keyboard_win_buttons_red)
            }else if (updateUp){
                resetRecordButton()
            }

        }
        createButton.setOnCheckedChangeListener { _, checkedId ->
            var updateStatus = false
            // 检查是否选中了按钮
            if (checkedId != -1) {
                val radioButton = view.findViewById<RadioButton>(checkedId) // 获取被选中的 RadioButton
                val buttonName = radioButton.text.toString() // 获取 RadioButton 的文本名称
                //当记录显示按钮为记录状态时，按键组的的选择不是与记录的表名相同时 则设为不记录状态
                GlobalVariable.macroStatus.observe(requireActivity()) { status ->
                    val (success, message) = status
                    if (success){
                        if (buttonName != message)  updateStatus = true
                    }
                }

                if (updateStatus){
                    resetRecordButton()
                }

            }

        }
        macroSend = view.findViewById(R.id.macroSend)
        macroSend.setOnClickListener{
            resetRecordButton()
            showSendDialog()
        }


    }

    override fun onResume() {
        super.onResume()
        getParameter()
        setParameterDisplay()
        setRadioGroupDisplay()

        refreshRadioGroupList()

        //恢复记录状态显示
        GlobalVariable.macroStatus.observe(requireActivity()) { status ->
            val (success, message) = status
            if (success){
                macroRecord.setBackgroundResource(R.drawable.keyboard_win_buttons_red)
                for (i in 0 until createButton.childCount) {
                    val radioButton = createButton.getChildAt(i) as RadioButton
                    if (radioButton.text.toString() == message) {
//                            createButton.check(radioButton.id) // 通过 id 来选中按钮
                        radioButton.isChecked = true //通过名称来选中
                        break
                    }
                }
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()

    }


    private fun initViews(view: View) {
        createButton = view.findViewById(R.id.createButton)
        pointerSeekBar = view.findViewById(R.id.pointerSeekBar)
        pointerTextView = view.findViewById(R.id.pointerTextView)

        iconOffsetSeekBar = view.findViewById(R.id.iconOffsetSeekBar)
        iconOffsetTextView = view.findViewById(R.id.iconOffsetTextView)
        screenLockSwitch = view.findViewById(R.id.screenLock)
        longKey = view.findViewById(R.id.longKey)

        retransmissionSwitch = view.findViewById(R.id.retransmission)
        xBackward = view.findViewById(R.id.xBackward)
        yBackward = view.findViewById(R.id.yBackward)
        movingDistanceSeekBar = view.findViewById(R.id.movingDistanceSeekBar)
        movingDistanceTextView = view.findViewById(R.id.movingDistanceTextView)

        gyroscopeThresholdSeekBar = view.findViewById(R.id.gyroscopeThresholdSeekBar)
        gyroscopeThresholdTextView = view.findViewById(R.id.gyroscopeThresholdTextView)

        resetButton = view.findViewById(R.id.resetButton)
        xMapping = view.findViewById(R.id.mouse_xMapping)
        yMapping = view.findViewById(R.id.mouse_yMapping)

    }

    private fun setupSwitch(switch: SwitchMaterial, parameterKey: String, onCheckedChange: (Boolean) -> Unit) {

        switch.setOnCheckedChangeListener { _, isChecked ->
            // 存储参数
            parameter.setParameter(parameterKey, isChecked)

            // 根据 switch 的状态执行对应操作
            onCheckedChange(isChecked)
        }
    }

    private fun setupSeekBar(seekBar: SeekBar,onProgress: (Int) -> Unit){

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    //取参数
    private fun getParameter(){
        pointer = parameter.getParameter("pointer",pointer) as Int
        iconOffset = parameter.getParameter("iconOffset",iconOffset) as Int
        movingDistance = parameter.getParameter("MovingDistance",movingDistance) as Int
        gyroscopeThreshold = parameter.getParameter("gyroscopeThreshold",gyroscopeThreshold) as Float
        screenLockSwitch.isChecked= parameter.getParameter("screenLock",false) as Boolean
        longKey.isChecked= parameter.getParameter("longKey",false) as Boolean
        retransmissionSwitch.isChecked= parameter.getParameter("retransmission",false) as Boolean
        xBackward.isChecked= parameter.getParameter("xBackward",true) as Boolean
        yBackward.isChecked= parameter.getParameter("yBackward",true) as Boolean
        xAxis = parameter.getParameter("xMapping",xAxis) as Int
        yAxis = parameter.getParameter("yMapping",yAxis) as Int

    }
    private fun setRadioGroupDisplay(){
        when(xAxis){
            0 -> xMapping.check(R.id.mouse_x_axis_x)
            1 -> xMapping.check(R.id.mouse_x_axis_y)
            2 -> xMapping.check(R.id.mouse_x_axis_z)
        }
        when(yAxis){
            0 -> yMapping.check(R.id.mouse_y_axis_x)
            1 -> yMapping.check(R.id.mouse_y_axis_y)
            2 -> yMapping.check(R.id.mouse_y_axis_z)
        }
    }
    //设置参数显示
    private fun setParameterDisplay(){

        pointerTextView.text = pointer.toString()
        pointerSeekBar.progress = pointer + 10

        iconOffsetTextView.text = iconOffset.toString()
        iconOffsetSeekBar.progress = iconOffset

        movingDistanceTextView.text = movingDistance.toString()
        movingDistanceSeekBar.progress = movingDistance

        gyroscopeThresholdTextView.text = gyroscopeThreshold.toString()
        gyroscopeThresholdSeekBar.progress = (gyroscopeThreshold * 100).toInt()

    }

    private fun setupSeekBars() {
        setupSeekBar(pointerSeekBar) { progress ->
            pointer = progress - 10
            if (pointer == -1 || pointer == 1) pointer = 0
            pointerTextView.text = pointer.toString()
            parameter.setParameter("pointer", pointer)
        }


        setupSeekBar(iconOffsetSeekBar) { progress ->
            iconOffset = if (progress == 0) 1 else progress
            iconOffsetTextView.text = iconOffset.toString()
            parameter.setParameter("iconOffset", iconOffset)
        }


        setupSeekBar(movingDistanceSeekBar) { progress ->
            movingDistance = if (progress < 1) 1 else progress
            movingDistanceTextView.text = movingDistance.toString()
            parameter.setParameter("MovingDistance", movingDistance)
        }


        setupSeekBar(gyroscopeThresholdSeekBar) { progress ->
            gyroscopeThreshold = if (progress == 0) 0.01f else progress / 100f
//            gyroscopeDeviationTextView.text = gyroscopeDeviation.toString()
            gyroscopeThresholdTextView.text = decimalFormat.format(gyroscopeThreshold) // 格式化输出
            parameter.setParameter("gyroscopeThreshold", gyroscopeThreshold)
        }


    }

    private fun createButton(string: String){
        // 创建新的 RadioButton
        val radioButton = RadioButton(requireActivity()).apply {
            id = View.generateViewId() // 为 RadioButton 生成唯一 ID
            text = string
            textSize = 16f // 设置文本大小
            setPadding(2, 2, 2, 2) // 设置内边距
            // 动态设置文本颜色
            setTextColor(resources.getColor(R.color.white, null))
            // 创建 ColorStateList 定义不同状态下的颜色
            val colorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked), // 选中状态
                    intArrayOf(-android.R.attr.state_checked) // 未选中状态
                ),
                intArrayOf(
                    ContextCompat.getColor(requireActivity(), R.color.blue), // 选中状态颜色
                    ContextCompat.getColor(requireActivity(), R.color.white) // 未选中状态颜色
                )
            )
            buttonTintList = colorStateList // 动态设置圆点颜色
            // 动态设置背景 drawable 资源
            setBackgroundResource(R.drawable.radio_background)
        }

        // 创建 LayoutParams 并设置宽度和高度
        val layoutParams = RadioGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,  // 宽度为 match_parent
            ViewGroup.LayoutParams.WRAP_CONTENT   // 高度为 wrap_content
        )

        // 将 LayoutParams 应用到 RadioButton
        radioButton.layoutParams = layoutParams
        // 将 RadioButton 添加到 RadioGroup 中
        createButton.addView(radioButton)

    }
    private fun  refreshRadioGroupList(){
        createButton.removeAllViews() // 删除所有按钮
        createButton.clearCheck()  //清除按键组选中状态
        //显示lists
        val tableNames = sendService.getAllTableNames()
        for (tableName in tableNames) {
            //android_metadata 和 sqlite_sequence 是系统管理的表，通常不应删除。
            //default_table 框架或工具生成的
            if (tableName !="android_metadata" && tableName !="sqlite_sequence" && tableName !="default_table"){
                createButton(tableName)
            }
        }
    }

    private fun msgError(string: String){
        val toast = Toast.makeText(requireContext(), null, Toast.LENGTH_SHORT)
        val layout = toast.view as LinearLayout?
        layout!!.setBackgroundResource(R.color.transparent)  //设置背景
        val text = layout.getChildAt(0) as TextView
        text.textSize = 20f  //设置字体大小
        text.setTextColor(requireContext().getColor(R.color.yellow))//设置字体颜色
        toast.setGravity(Gravity.CENTER, 0, 0)//显示的位置
        toast.setText(string)  //显示内容
        toast.show()

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            toast.cancel()
        }, 1000.toLong())  // 自定义时间指定 Toast 显示的时长，单位为毫秒。

    }
    // 检查表名是否合法
    private fun isValidTableName(tableName: String): Boolean {
        // 1. 使用正则表达式检查表名格式（不能包含空格）
        if (!tableName.matches(Regex(tableNamePattern))) {
            return false
        }
        // 2. 检查表名是否与关键字冲突
        if (sqlKeywords.contains(tableName.lowercase())) {
            return false
        }

        return true
    }
    private fun showAddDialog() {
        // 使用 LayoutInflater 加载自定义布局
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.macro_add, null)
        val positive: Button = dialogView.findViewById(R.id.macro_add_execute)
        val cancel: Button = dialogView.findViewById(R.id.macro_add_cancel)
        val inputText: EditText = dialogView.findViewById(R.id.macro_add_input)
        var valid = false
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
//            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.black) //设置背景
        dialog.show()
        // 延迟 100 毫秒请求焦点，确保视图已完全加载
        inputText.postDelayed({
            inputText.requestFocus() //获取焦点
            //需要键盘自动弹出
            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT)
        }, 100)

        // 设置按钮的点击事件
        positive.setOnClickListener {
            if (valid){
                if (sendService.createNewTable(inputText.text.toString())){
                    createButton(inputText.text.toString())
                    dialog.dismiss() // 点击后关闭对话框
                }else{
                    dialogView.findViewById<EditText>(R.id.macro_add_input).text.clear()
                }
            }
        }
        cancel.setOnClickListener {
            dialog.dismiss()
        }

        // 添加 TextWatcher 来实时监听 EditText 内容变化
        inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable?) {
                // 获取输入的文本并去除首尾空格
                val tableName = editable.toString()
                // 先删除所有开头和中间的空格，但只会删除在用户输入新的字符时（避免清空非空字符）
                val tableNameWithoutSpaces = tableName.replaceFirst("^\\s+".toRegex(), "") // 删除开头的空格
                // 如果输入内容发生变化，并且删除了空格，则更新 EditText 内容
                if (tableName != tableNameWithoutSpaces) {
                    // 清空内容并更新输入框，删除开头的空格
                    editable?.clear()
                    editable?.append(tableNameWithoutSpaces)
                }
                if (tableName.isEmpty()) {
                    valid = false
                } else if (!isValidTableName(tableName)) {
                    valid = false
                    msgError(getString(R.string.invalid))
                    //删除刚刚输入的字符
                    if (!editable.isNullOrEmpty()) {
                        editable.delete(editable.length - 1, editable.length)  // 删除最后一个字符
                    }
                } else {
                    valid = true
                }

            }
        })



    }
    private fun showDeleteDialog() {
        // 使用 LayoutInflater 加载自定义布局
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.macro_delete, null)
        val positive: Button = dialogView.findViewById(R.id.macro_delete_execute)
        val cancel: Button = dialogView.findViewById(R.id.macro_delete_cancel)
        val name: TextView = dialogView.findViewById(R.id.macro_delete)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
//            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.black) //设置背景

        val getId = createButton.checkedRadioButtonId
        if (getId != -1) {
            // 如果有按钮被选中
            val button: RadioButton = requireView().findViewById(getId)
            val buttonText = button.text.toString()
            name.text = buttonText
            dialog.show()
        } else {
            msgError(getString(R.string.select))
            dialog.dismiss() //如果没有按钮被选中 关闭对话框
        }
        // 设置按钮的点击事件
        positive.setOnClickListener {
            if (name.text.isNotEmpty()){
                if (sendService.deleteTable(name.text.toString())){
                    refreshRadioGroupList()
                    dialog.dismiss() // 点击后关闭对话框
                }
            }
        }
        cancel.setOnClickListener {
            dialog.dismiss() // 点击后关闭对话框
        }
    }
    private fun showSendDialog() {
        lateinit var data : List<Triple<ByteArray, String, Long>>
        var sendJob: Job? = null
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.macro_send, null)
        val positive: Button = dialogView.findViewById(R.id.macro_send_execute)
        val cancel: Button = dialogView.findViewById(R.id.macro_send_cancel)
        val name: TextView = dialogView.findViewById(R.id.macro_send_name)
        val sendSpeedText: TextView = dialogView.findViewById(R.id.macro_send_SpeedText)
        val sendCountText: TextView = dialogView.findViewById(R.id.macro_send_CountText)
        val completionText: TextView = dialogView.findViewById(R.id.send_completion)
        completionText.text = getString(R.string.completion)
        val completionBar: ProgressBar = dialogView.findViewById(R.id.send_progressBar)

        val sendSpeedBar : SeekBar = dialogView.findViewById(R.id.macro_send_SpeedBar)
        sendSpeedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sendSpeedText.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val sendCountBar : SeekBar = dialogView.findViewById(R.id.macro_send_CountBar)
        sendCountBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress < 1) {
                    sendCountText.text = 1.toString()
                }else sendCountText.text = progress.toString()

            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)  // 禁止外部点击
            .create()
        // 禁止返回键关闭对话框
        dialog.setOnKeyListener { _, keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> true // 禁止返回键关闭对话框
                KeyEvent.KEYCODE_VOLUME_UP -> true // 禁止音量上键
                KeyEvent.KEYCODE_VOLUME_DOWN -> true // 禁止音量下键
                KeyEvent.KEYCODE_POWER -> true // 禁止电源键（有些情况下这可能不可完全禁止）
                else -> false  // 允许其他按键
            }
        }
        dialog.window?.setBackgroundDrawableResource(R.color.black) //设置背景

        val getId = createButton.checkedRadioButtonId
        if (getId != -1) {
            // 如果有按钮被选中
            val button: RadioButton = requireView().findViewById(getId)
            val buttonText = button.text.toString()
            name.text = buttonText
            data = sendService.getDataFromTable(buttonText)//获取表中数据
            dialog.show()
        } else {
            msgError(getString(R.string.select))
            dialog.dismiss() //如果没有按钮被选中 关闭对话框
        }
        // 设置按钮的点击事件
        positive.setOnClickListener {
            if (name.text.isNotEmpty()){
                sendJob?.takeIf { it.isActive }?.cancel()  // 如果协程已经存在，取消它
                // 创建新的协程任务,这个要更新UI显示
                sendJob = CoroutineScope(Dispatchers.Main).launch {
                    val count = sendCountBar.progress.coerceAtLeast(1) // 确保次数至少为 1
                    val speed = sendSpeedBar.progress
                    // 计算最大进度值
                    val max = count * data.size
                    var progress = 0
                    completionBar.max = max

                    for (i in 1..count) {
                        // 遍历数据
                        for (item in data){
                            // 获取每一行的数据
                            val byteArray = item.first   //数据内容
//                            val string = item.second   //表名
                            val longValue = item.third   //延时时间
                            if (speed > 0){
                                var time = longValue / speed
                                if (time < 1) time = 1
                                delay(time)
                            }else {
                                delay(1)
                            }

                            var put = sendService.setReporterData(byteArray, "")
                            while (!put){
                                delay(10)
                                put = sendService.setReporterData(byteArray, "")
                            }
                            // 更新进度
                            completionBar.progress = progress++
                            completionText.text = "${String.format("%.1f", (progress.toFloat()/ max) * 100)}% ${getString(R.string.completion)}"

                        }
                    }
                    // 任务完成后，可以做一些处理
                    dialog.dismiss() // 关闭对话框
                }

            }
        }
        cancel.setOnClickListener{
            val key = ByteArray(8)
            val mouse = ByteArray(8)
            mouse[6] = 0xff.toByte()
            mouse[7] = 0xff.toByte()
            sendJob?.takeIf { it.isActive }?.cancel()
            sendService.clearQueue(key,mouse)//先清空队列,防止数据无法提交
            dialog.dismiss()

        }
    }
    //记录按键恢复
    private fun resetRecordButton(){
        GlobalVariable.setMacroStatus(false, "")
        macroRecord.setBackgroundResource(R.drawable.keyboard_win_buttons_up)
    }

}