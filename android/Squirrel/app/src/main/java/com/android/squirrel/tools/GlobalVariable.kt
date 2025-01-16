package com.android.squirrel.tools

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.viewpager2.widget.ViewPager2
import com.android.squirrel.service.SendServices
//全局变量  全局单例模式
object  GlobalVariable {

    // 存储服务的实例
    private val _serviceLiveData = MutableLiveData<SendServices>()
    val serviceLiveData: LiveData<SendServices> get() = _serviceLiveData
    fun setService(service: SendServices) {
        _serviceLiveData.value = service
    }

    // ViewPager2 实例
    private val _viewPager = MutableLiveData<ViewPager2>()
    val viewPager: LiveData<ViewPager2> get() = _viewPager
    fun setViewPager(viewPager: ViewPager2) {
        _viewPager.value = viewPager
    }

    //鼠标触摸 与 传感器 按钮的状态
    private val _mouseButtonReset = MutableLiveData<Boolean>(false)  // 默认值为 false
    val mouseButtonReset: LiveData<Boolean> get() = _mouseButtonReset
    fun setMouseButtonReset(data: Boolean) {
        _mouseButtonReset.value = data
    }


    // 宏记录的状态 (Boolean, String)  ServicesInstance
    private val _macroStatus = MutableLiveData<Pair<Boolean, String>>(Pair(false, ""))
    val macroStatus: LiveData<Pair<Boolean, String>> get() = _macroStatus

    fun setMacroStatus(success: Boolean, message: String) {
        _macroStatus.value = Pair(success, message)
    }



}