package com.android.squirrel.keyboard

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.android.squirrel.MainActivity
import com.android.squirrel.R
import com.android.squirrel.service.SendServices
import com.android.squirrel.tools.BoundedByteDeque
import com.android.squirrel.tools.GlobalVariable
import com.android.squirrel.tools.SharedDataPermanently


class KeyboardWindow : Fragment(), View.OnTouchListener, View.OnLongClickListener {
    private  var viewPager: ViewPager2? = null
    private var sendService: SendServices? = null//服务
    private val keyCode = BoundedByteDeque(6)
    private var modifierKeysData : Byte = 0
    private var tableNameMessage = ""
    private var longKey = false
    private var handler: Handler? = null
    private var isRunning = false
    private var runnable: Runnable? = null

    private var keyFn = false  //fn按键状态
    private val fnConvert= hashMapOf<Int, ConvertData>()
    private var keySymbol = false  //fn按键状态
    private val symbolConvert= hashMapOf<Int, ConvertData>()

    private lateinit var  horizontalView: HorizontalScrollView
    private lateinit var linearLayout: LinearLayout

    data class ConvertData(
        val id:Int,
        val upName:String,
        val upTag: String,
        val downName:String,
        val downTag: String
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.keyboard_window, container, false)
        return view
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        GlobalVariable.viewPager.observe(viewLifecycleOwner) { value ->
            viewPager = value
        }
        GlobalVariable.serviceLiveData.observe(viewLifecycleOwner) { service ->
            sendService = service
        }

        /** 找不到的好的方法 页面拖动总是会发一个字符  因为按键的触摸按下时发生了viewPager的拖动事件后按键的触摸抬起无法完成检测*/
//    viewPager!!.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
//        override fun onPageScrollStateChanged(state: Int) {
//            super.onPageScrollStateChanged(state)
//            // 页面正在被用户拖动 （只是减小长按检测机率）
//            if (state == ViewPager2.SCROLL_STATE_DRAGGING){
//                keyCode.removeAll() //清空数据
//                modifierKeysData = 0
//                sendData()
//            }
//        }
//    })


        /**键盘按键事件注册 */
        for (i in 0..39) {
            @SuppressLint("DiscouragedApi") val resId = resources.getIdentifier(
                "keyboard_button_horizontal_win_$i", "id",
                requireContext().packageName
            )
            if (resId != 0) {
                view.findViewById<Button>(resId).setOnTouchListener(this)
                view.findViewById<Button>(resId).setOnLongClickListener(this)

            }
        }

        setKeyFnData()
        setKeySymbolData()


        horizontalView = view.findViewById(R.id.horizontalScrollView)
        linearLayout = view.findViewById(R.id.horizontalLinearLayout)





    }

    override fun onResume() {
        super.onResume()

        GlobalVariable.macroStatus.observe(requireActivity()) { status ->
            val (success, message) = status
            tableNameMessage = message
        }
        viewPager?.setUserInputEnabled(false)
        val parameter = SharedDataPermanently("parameter",requireActivity())
        longKey =  parameter.getParameter("longKey",false) as Boolean
    }


    override fun onStop() {
        super.onStop()
        viewPager?.setUserInputEnabled(true)

    }

    private fun sendData(){
        val reportDescriptor = ByteArray(8) //键盘报告符
        reportDescriptor[0] = modifierKeysData
        System.arraycopy(keyCode.getAll(), 0, reportDescriptor, 2, keyCode.getAll().size) //拼接数据

        sendService?.setReporterData(reportDescriptor,tableNameMessage) //提交数据

    }

    // 扩展函数：在 Byte 中指定的位进行取反
    private fun Byte.toggleBit(bitIndex: Int): Byte {
        require(bitIndex in 0..7) { "bitIndex must be between 0 and 7" }
        val mask = 1 shl bitIndex
        return (this.toInt() xor mask).toByte()
    }


    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                stopTimer()
                if(v?.id != 0){
                    val button = v?.id?.let { view?.findViewById<Button>(it) }
                    addButtonMsg(button?.text.toString(),true)
                }
                when(v?.id){
                    R.id.keyboard_button_horizontal_win_35 -> {} //全屏
                    R.id.keyboard_button_horizontal_win_36 -> {} //fn
                    R.id.keyboard_button_horizontal_win_37 -> {} //num
                    R.id.keyboard_button_horizontal_win_31 -> {
                        modifierKeysData = modifierKeysData.toggleBit(1)
                        sendData()
                    } //Shift
                    R.id.keyboard_button_horizontal_win_32 -> {
                        modifierKeysData = modifierKeysData.toggleBit(0)
                        sendData()
                    } //Ctrl
                    R.id.keyboard_button_horizontal_win_38 -> {
                        modifierKeysData = modifierKeysData.toggleBit(3)
                        sendData()
                    }//WIN
                    R.id.keyboard_button_horizontal_win_33 -> {
                        modifierKeysData = modifierKeysData.toggleBit(6)
                        sendData()
                    } //   右边Alt 是 6  左边Alt 是2
                    else -> {
                        val keyPad = java.lang.Byte.toUnsignedInt(v?.tag.toString().toInt(16).toByte())
                        keyCode.add(keyPad.toByte())
                        sendData()
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                stopTimer()
                if(v?.id != 0){
                    val button = v?.id?.let { view?.findViewById<Button>(it) }
                    addButtonMsg(button?.text.toString(),false)
                }

                when(v?.id){
                    R.id.keyboard_button_horizontal_win_35 -> {
                        val intent = Intent(requireContext(), KeyboardFullScreen::class.java)
                        startActivity(intent)
                    } //全屏
                    R.id.keyboard_button_horizontal_win_36 -> {

                        keySymbol = false
                        val symbol = view?.findViewById<Button>(R.id.keyboard_button_horizontal_win_37)
                        buttonsModify(keySymbol,symbol,symbolConvert)

                        keyFn = !keyFn
                        val fn = view?.findViewById<Button>(R.id.keyboard_button_horizontal_win_36)
                        buttonsModify(keyFn,fn,fnConvert)

                    } //fn
                    R.id.keyboard_button_horizontal_win_37 -> {
                        keyFn = false
                        val fn = view?.findViewById<Button>(R.id.keyboard_button_horizontal_win_36)
                        buttonsModify(keyFn,fn,fnConvert)

                        keySymbol = !keySymbol
                        val symbol = view?.findViewById<Button>(R.id.keyboard_button_horizontal_win_37)
                        buttonsModify(keySymbol,symbol,symbolConvert)

                    }
                    R.id.keyboard_button_horizontal_win_31 -> {
                        modifierKeysData = modifierKeysData.toggleBit(1)
                        sendData()
                    } //Shift
                    R.id.keyboard_button_horizontal_win_32 -> {
                        modifierKeysData = modifierKeysData.toggleBit(0)
                        sendData()
                    } //Ctrl
                    R.id.keyboard_button_horizontal_win_38 -> {
                        modifierKeysData = modifierKeysData.toggleBit(3)
                        sendData()
                    }//WIN
                    R.id.keyboard_button_horizontal_win_33 -> {
                        modifierKeysData = modifierKeysData.toggleBit(6)
                        sendData()
                    } //   右边Alt 是 6  左边Alt 是2
                    else -> {
                        keyCode.removeAll() //清空数据
                        sendData()
                    }
                }
            }
        }
        return false
    }

    override fun onLongClick(v: View?): Boolean {
        when(v?.id){
            R.id.keyboard_button_horizontal_win_35 -> {} //全屏
            R.id.keyboard_button_horizontal_win_36 -> {} //fn
            R.id.keyboard_button_horizontal_win_37 -> {} //num
            R.id.keyboard_button_horizontal_win_31 -> {} //Shift
            R.id.keyboard_button_horizontal_win_32 -> {} //Ctrl
            R.id.keyboard_button_horizontal_win_38 -> {}//WIN
            R.id.keyboard_button_horizontal_win_33 -> {} //   右边Alt 是 6  左边Alt 是2
            else -> {
                if (longKey){
                    val keyPad = java.lang.Byte.toUnsignedInt(v?.tag.toString().toInt(16).toByte())
                    startTimer(keyPad)
                }
            }
        }
        return false
    }

    // 启动定时任务，先停止已有任务，再启用新的定时任务
    private fun startTimer(param: Int) {
        // 如果已有定时任务在运行，先停止它
        stopTimer()

        // 创建 Handler，并且使用主线程的 Looper
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    keyCode.removeAll()
                    keyCode.addFirst(param.toByte())
                    sendData()
                    handler?.postDelayed(this, 50)
                }
            }
        }
        // 启动定时任务
        isRunning = true
        handler?.post(runnable!!)
    }

    // 停止定时任务
    private fun stopTimer() {
        isRunning = false
        handler?.removeCallbacks(runnable!!) // 移除所有未执行的回调任务
    }

    private fun addButtonMsg(name:String,state:Boolean){
        val button = Button(requireContext()).apply {
            text = name
            setTextColor(resources.getColor(R.color.grey, null))
            if (state){
                setBackgroundResource(R.drawable.keyboard_win_buttons_down)
            } else {
                setBackgroundResource(R.drawable.keyboard_win_buttons_up)
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        linearLayout.addView(button)
        horizontalView.post {
            horizontalView.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private  fun buttonsModify(state:Boolean,key:Button?,convert: HashMap<Int,ConvertData>){
        when(state){
            true -> {
                key?.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.keyboard_win_buttons_down
                )

                convert.forEach { (_,data) ->
                    val keyButton = view?.findViewById<Button>(data.id)
                    keyButton?.text = data.downName
                    keyButton?.tag = data.downTag

                }


            }

            false -> {
                key?.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.keyboard_win_buttons_up
                )

                convert.forEach { (_,data) ->
                    val keyButton = view?.findViewById<Button>(data.id)
                    keyButton?.text = data.upName
                    keyButton?.tag = data.upTag
                }
            }
        }

    }

    /**
     * 按键与抬起Fn 键后 要改变的其他按键的内容
     * getIdentifier 中的 name 为按键的ID
     * */
    private fun  setKeyFnData(){

        fnConvert[0] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_0", "id", requireContext().packageName),
            "A",
            "04",
            "F1",
            "3A"
        )

        fnConvert[1] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_1", "id", requireContext().packageName),
            "B",
            "05",
            "F2",
            "3B"
        )

        fnConvert[2] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_2", "id", requireContext().packageName),
            "C",
            "06",
            "F3",
            "3C"
        )

        fnConvert[3] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_3", "id", requireContext().packageName),
            "D",
            "07",
            "F4",
            "3D"
        )

        fnConvert[4] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_4", "id", requireContext().packageName),
            "E",
            "08",
            "F5",
            "3E"
        )

        fnConvert[5] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_5", "id", requireContext().packageName),
            "F",
            "09",
            "F6",
            "3F"
        )

        fnConvert[6] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_6", "id", requireContext().packageName),
            "G",
            "0A",
            "F7",
            "40"
        )

        fnConvert[7] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_7", "id", requireContext().packageName),
            "H",
            "0B",
            "F8",
            "41"
        )

        fnConvert[8] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_8", "id", requireContext().packageName),
            "I",
            "0C",
            "F9",
            "42"
        )

        fnConvert[9] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_9", "id", requireContext().packageName),
            "J",
            "0D",
            "F10",
            "43"
        )

        fnConvert[10] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_10", "id", requireContext().packageName),
            "K",
            "0E",
            "F11",
            "44"
        )


        fnConvert[11] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_11", "id", requireContext().packageName),
            "L",
            "0F",
            "F12",
            "45"
        )
    }

    private fun  setKeySymbolData(){

        symbolConvert[0] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_0", "id", requireContext().packageName),
            "A",
            "04",
            "~\n\n`",
            "35"
        )

        symbolConvert[1] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_1", "id", requireContext().packageName),
            "B",
            "05",
            "!\n\n1",
            "1E"
        )

        symbolConvert[2] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_2", "id", requireContext().packageName),
            "C",
            "06",
            "&\n\n2",
            "1F"
        )

        symbolConvert[3] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_3", "id", requireContext().packageName),
            "D",
            "07",
            "#\n\n3",
            "20"
        )

        symbolConvert[4] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_4", "id", requireContext().packageName),
            "E",
            "08",
            "$\n\n4",
            "21"
        )

        symbolConvert[5] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_5", "id", requireContext().packageName),
            "F",
            "09",
            "%\n\n5",
            "22"
        )

        symbolConvert[6] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_6", "id", requireContext().packageName),
            "G",
            "0A",
            "^\n\n6",
            "23"
        )

        symbolConvert[7] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_7", "id", requireContext().packageName),
            "H",
            "0B",
            "&\n\n7",
            "24"
        )

        symbolConvert[8] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_8", "id", requireContext().packageName),
            "I",
            "0C",
            "*\n\n8",
            "25"
        )

        symbolConvert[9] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_9", "id", requireContext().packageName),
            "J",
            "0D",
            "(\n\n9",
            "26"
        )

        symbolConvert[10] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_10", "id", requireContext().packageName),
            "K",
            "0E",
            ")\n\n0",
            "27"
        )


        symbolConvert[11] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_11", "id", requireContext().packageName),
            "L",
            "0F",
            "—\n\n-",
            "2D"
        )
        symbolConvert[12] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_12", "id", requireContext().packageName),
            "M",
            "10",
            "+\n\n=",
            "2E"
        )
        symbolConvert[13] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_13", "id", requireContext().packageName),
            "N",
            "11",
            "{\n\n[",
            "2F"
        )
        symbolConvert[14] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_14", "id", requireContext().packageName),
            "O",
            "12",
            "}\n\n]",
            "30"
        )
        symbolConvert[15] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_15", "id", requireContext().packageName),
            "P",
            "13",
            "|\n\n\\",
            "31"
        )
        symbolConvert[16] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_16", "id", requireContext().packageName),
            "Q",
            "14",
            ":\n\n;",
            "33"
        )
        symbolConvert[17] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_17", "id", requireContext().packageName),
            "R",
            "15",
            "\"\n\n'",
            "34"
        )

        symbolConvert[18] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_18", "id", requireContext().packageName),
            "S",
            "16",
            "<\n\n,",
            "36"
        )


        symbolConvert[19] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_19", "id", requireContext().packageName),
            "T",
            "17",
            ">\n\n.",
            "37"
        )
        symbolConvert[20] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_20", "id", requireContext().packageName),
            "U",
            "18",
            "?\n\n/",
            "38"
        )
        symbolConvert[21] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_21", "id", requireContext().packageName),
            "V",
            "19",
            "↑",
            "52"
        )


        symbolConvert[22] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_25", "id", requireContext().packageName),
            "Z",
            "1D",
            "←",
            "50"
        )
        symbolConvert[23] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_26", "id", requireContext().packageName),
            "Tab",
            "2B",
            "↓",
            "51"
        )

        symbolConvert[24] = ConvertData(
            resources.getIdentifier("keyboard_button_horizontal_win_27", "id", requireContext().packageName),
            "Caps\nLock",
            "39",
            "→",
            "4F"
        )


    }


}


