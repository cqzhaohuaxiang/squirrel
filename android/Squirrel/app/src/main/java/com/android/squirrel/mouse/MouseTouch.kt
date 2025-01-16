package com.android.squirrel.mouse



import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.android.squirrel.R
import com.android.squirrel.service.SendServices
import com.android.squirrel.tools.GlobalVariable
import com.android.squirrel.tools.SharedDataPermanently
import kotlin.math.abs
import kotlin.math.round

/**
 * 鼠标报告符数据内容
 *  byte 0 记录按键状态
 *      bit0: 左键    bit1: 右键   bit2: 中键    bit3: 后退键      bit4: 前进键
 *      bit5: 无  bit6: 无 bit7: 无
 *  byte 1  (左 右 指针)
 *  byte 2  ( 上 下 指针)
 *  byte 3  (上 下 滚动)
 *  byte 4  (左 右 滚动)
 **/
class MouseTouch() : Fragment(){

    private var viewPager: ViewPager2? = null
    private var sendService: SendServices? = null//服务
    private var tableNameMessage = ""
    private lateinit var touchIcon: ImageView

    private val reportDescriptor = ByteArray(5)
    private var toggleSwitch = true  //指针与滚轮之间转换
    private lateinit var toggleText : TextView //提示一下当前状态
    private var offset = 200
    private var pointer = 0
    private var movingDistance = 50 //移动距离
    private var x = 0f
    private var y = 0f

    private val moveWheel = IntArray(2)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {


        return inflater.inflate(R.layout.mouse_touch, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        GlobalVariable.viewPager.observe(viewLifecycleOwner) { data ->
            viewPager = data
        }
        GlobalVariable.serviceLiveData.observe(viewLifecycleOwner) { service ->
            sendService = service
        }

        touchIcon = view.findViewById(R.id.mouse_touch_msg)
        toggleText = view.findViewById(R.id.mouse_touch_toggle)


        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleSwitch = !toggleSwitch
                when(toggleSwitch){
                    true ->  toggleText.text = getString(R.string.pointer)
                    false -> toggleText.text = getString(R.string.wheel)
                }
                return super.onDoubleTap(e)
            }
        })
        val touchZone = view.findViewById<RelativeLayout>(R.id.touch_zone)
        touchZone.setOnTouchListener{_, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action){
                //第一根手指按下时
                MotionEvent.ACTION_DOWN -> {
                    x = event.x
                    y = event.y
                    when(toggleSwitch){
                        true ->  touchIcon.setImageResource(R.drawable.image_mouse_pointer) // 设置背景图片
                        false -> touchIcon.setImageResource(R.drawable.image_mouse_roller) // 设置背景图片
                    }
                    moveImageView(event.x,event.y - offset)
                }
                //手指移动时
                MotionEvent.ACTION_MOVE -> {
                    handlingFingerMovements( event.x - x,event.y - y)
                    moveImageView(event.x,event.y - offset)
                    x = event.x
                    y = event.y

                }

                MotionEvent.ACTION_UP -> {
                    //最后一根手指抬起时 处理roller的方向
                    touchIcon.setImageResource(0)// 取消图片显示
                }

            }

           true
        }

        //左 右 按键注册
        setupButton(view,R.id.mouse_touch_left, 0)
        setupButton(view,R.id.mouse_touch_right, 1)
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
        viewPager?.setUserInputEnabled(false)
        val parameter = SharedDataPermanently("parameter",requireContext())
        offset = parameter.getParameter("iconOffset",offset) as Int
        pointer = parameter.getParameter("pointer",pointer) as Int
        movingDistance = parameter.getParameter("MovingDistance",movingDistance) as Int

        GlobalVariable.macroStatus.observe(requireActivity()) { status ->
            val (success, message) = status
            tableNameMessage = message
        }
    }

    override fun onStop() {
        super.onStop()
        viewPager?.setUserInputEnabled(true)

    }


    private fun moveImageView(x :Float,y:Float) {
        touchIcon.animate()
            .x(x)
            .y(y)
            .setDuration(0)
            .start()
    }


    private fun Byte.toggleBit(bitIndex: Int): Byte {
        require(bitIndex in 0..7) { "bitIndex must be between 0 and 7" }
        val mask = 1 shl bitIndex
        return (this.toInt() xor mask).toByte()
    }

    //处理手指移动动作
    private fun handlingFingerMovements(moveX:Float,moveY:Float){
        var x = 0
        var y = 0
        when(pointer){
            0 -> {
                x = round(moveX).toInt()
                y = round(moveY).toInt()
            }
            in 1..10-> {
                x = round(moveX * pointer).toInt()
                y = round(moveY * pointer).toInt()
            }
            else -> {
                x = round(moveX / abs(pointer)).toInt()
                y = round(moveY / abs(pointer)).toInt()
            }
        }

        when(toggleSwitch){
            true -> {
                reportDescriptor[1] = x.toByte()
                reportDescriptor[2] = y.toByte()
                sendData()
            }
            false -> {
                moveWheel[0] += x
                moveWheel[1] += y
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

    private fun sendData(){
        sendService?.setReporterData(reportDescriptor,tableNameMessage) //提交数据
    }


}