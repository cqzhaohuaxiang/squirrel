package com.android.squirrel


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.android.squirrel.mouse.MouseSensor
import com.android.squirrel.mouse.OnMouse
import com.android.squirrel.service.SendServices
import com.android.squirrel.tools.GlobalVariable
import com.android.squirrel.tools.SharedDataPermanently
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator


class MainActivity : AppCompatActivity(){

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    // 定义图标资源  没有选中的状态
    private val defaultIcons = intArrayOf(
        R.drawable.image_keyboard_white,
        R.drawable.image_mouse_white,
        R.drawable.image_camera_white,
        R.drawable.image_set_white,
        R.drawable.image_help_white
    )
    //选中的状态
    private val selectedIcons = intArrayOf(
        R.drawable.image_keyboard_blue,
        R.drawable.image_mouse_blue,
        R.drawable.image_camera_blue,
        R.drawable.image_set_blue,
        R.drawable.image_help_blue
    )

    private lateinit var sendService: SendServices
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val intent = Intent(this, SendServices::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        tabLayout = findViewById(R.id.main_tab_layout)
        viewPager = findViewById(R.id.main_view_pager)
        // 预加载页面
//        viewPager.offscreenPageLimit = 1
        // 确保 viewPager 和 tabLayout 已经初始化
        viewPager.adapter = PagerAdapter(supportFragmentManager, lifecycle)
        //提交ViewPager2 句柄
        viewPager.let {
            GlobalVariable.setViewPager(viewPager)
        }



        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.setIcon(defaultIcons[position])
        }.attach()

        tabLayout.setSelectedTabIndicatorColor(getColor(R.color.blue)) // 设置下划线选中时的颜色
        // 设置 TabLayout 的选项卡选择监听器
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val position = it.position
                    it.setIcon(selectedIcons[position])  // 设置选中图标
                    viewPager.setUserInputEnabled(true) //启用页面滑动
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.let {
                    val position = it.position
                    it.setIcon(defaultIcons[position])  // 设置默认图标
                }
            }
            // 处理选项卡被重新选择的事件（如果需要）
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })


        // 都是设置默认显示
//        val initialTabIndex = 1 // Tab 2 (0-based index)
//        viewPager.currentItem = initialTabIndex

//        tabLayout.getTabAt(1)?.select()

    }

    override fun onResume() {
        super.onResume()
        val parameter = SharedDataPermanently("parameter",this)
        val screenLock =  parameter.getParameter("screenLock",false) as Boolean
        if (screenLock) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) //不自动锁屏
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as SendServices.LocalBinder
            sendService = binder.getService()
            bound = true
            // 将服务实例保存
            GlobalVariable.setService(sendService)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
        }
    }


    // 重写 dispatchKeyEvent 方法以处理音量键事件
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val action = event.action
            // 获取当前显示的 OnMouse Fragment
            val onMouseFragment = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}") as? OnMouse
            // 获取 OnMouse Fragment 内部的 MouseSensor Fragment
            val mouseSensorFragment = onMouseFragment?.childFragmentManager?.findFragmentById(R.id.mouse_fragment_container) as? MouseSensor

            if (action == KeyEvent.ACTION_DOWN) {
                // 处理按下事件
                mouseSensorFragment?.handleVolumeKeyPress(event.keyCode)
            } else if (action == KeyEvent.ACTION_UP) {
                // 处理抬起事件
                mouseSensorFragment?.handleVolumeKeyRelease(event.keyCode)
            }

            // 返回 true 表示事件已被处理
            return true
        }
        // 交给父类处理其他事件
        return super.dispatchKeyEvent(event)
    }

}
