package com.android.squirrel.keyboard

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.squirrel.MainActivity
import com.android.squirrel.R
import com.android.squirrel.service.SendServices
import com.android.squirrel.tools.BoundedByteDeque
import com.android.squirrel.tools.GlobalVariable
import com.android.squirrel.tools.SharedDataPermanently

class KeyboardFullScreen : AppCompatActivity()
    ,View.OnTouchListener, View.OnLongClickListener {
    private var soundSwitch = false
    private lateinit var soundPrompts : PlaySound
    private var sendService: SendServices? = null
    private var tableNameMessage = ""
    private var bound = false
    private val keyCode = BoundedByteDeque(6) // 按键代码（6 个字节，每个字节 8 位）：表示当前按下的键的扫描码。最多支持 6 个同时按下的键。
    //修饰符键（8 位）：表示修饰符键的状态（例如 Ctrl、Shift、Alt 等）
    private var modifierKeysData : Byte = 0

    private var longKey = false
    private var handler: Handler? = null
    private var isRunning = false
    private var runnable: Runnable? = null
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundPrompts = PlaySound(this) //播放声音
        setContentView(R.layout.keyboard_full_screen)
        val intent = Intent(this, SendServices::class.java)
//        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        /**键盘按键们事件注册 */
        for (i in 0..75) {
            @SuppressLint("DiscouragedApi") val resId = resources.getIdentifier(
                "keyboard_button_full_$i", "id",
                packageName
            )
            if (resId != 0) {
                findViewById<Button>(resId).setOnTouchListener(this)
                findViewById<Button>(resId).setOnLongClickListener(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val parameter = SharedDataPermanently("parameter",this)
        val screenLock =  parameter.getParameter("screenLock",false) as Boolean
        longKey =  parameter.getParameter("longKey",false) as Boolean
        if (screenLock) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) //不自动锁屏
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        hideSystemUI()

        GlobalVariable.macroStatus.observe(this) { status ->
            val (success, message) = status
            tableNameMessage = message
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        soundPrompts.release() //释放提示的资源
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }





    //隐藏虚拟按键与状态栏 综合方法
    private fun hideSystemUI() {

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }



    }


    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as SendServices.LocalBinder
            sendService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
        }
    }



    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {

        when(event?.action){
            MotionEvent.ACTION_DOWN -> {
                stopTimer()
                soundPrompts.play(soundSwitch)//播放提示
                when(v?.id){
                    R.id.keyboard_button_full_74 -> {}
                    R.id.keyboard_button_full_75 -> {}
                    R.id.keyboard_button_full_55 -> {
                        modifierKeysData = modifierKeysData.toggleBit(1)
                        sendData()
                    } //Shift
                    R.id.keyboard_button_full_67 -> {
                        modifierKeysData = modifierKeysData.toggleBit(0)
                        sendData()
                    } //Ctrl
                    R.id.keyboard_button_full_68 -> {
                        modifierKeysData = modifierKeysData.toggleBit(3)
                        sendData()
                    }//WIN
                    R.id.keyboard_button_full_69 -> {
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
                soundPrompts.stop()
                stopTimer()
                when(v?.id){
                    R.id.keyboard_button_full_74 -> {
                        soundSwitch =! soundSwitch
                        val soundButton = findViewById<Button>(R.id.keyboard_button_full_74)
                        when(soundSwitch){
                            true -> soundButton.background = ContextCompat.getDrawable(this, R.drawable.keyboard_button_sound_down)
                            false -> soundButton.background = ContextCompat.getDrawable(this, R.drawable.keyboard_button_sound_up)
                        }
                    }
                    R.id.keyboard_button_full_75 -> {

                        intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                    }
                    R.id.keyboard_button_full_55 -> {
                        modifierKeysData = modifierKeysData.toggleBit(1)
                        sendData()
                    } //Shift
                    R.id.keyboard_button_full_67 -> {
                        modifierKeysData = modifierKeysData.toggleBit(0)
                        sendData()
                    } //Ctrl
                    R.id.keyboard_button_full_68 -> {
                        modifierKeysData = modifierKeysData.toggleBit(3)
                        sendData()
                    }//WIN
                    R.id.keyboard_button_full_69 -> {
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
            R.id.keyboard_button_full_74 -> {}
            R.id.keyboard_button_full_75 -> {}
            R.id.keyboard_button_full_55 -> {} //Shift
            R.id.keyboard_button_full_67 -> {} //Ctrl
            R.id.keyboard_button_full_68 -> {}//WIN
            R.id.keyboard_button_full_69 -> {} //   右边Alt 是 6  左边Alt 是2
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




}


