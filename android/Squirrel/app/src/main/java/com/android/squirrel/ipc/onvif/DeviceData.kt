package com.android.squirrel.ipc.onvif


/**
 * 给标签显示用的信息
 * deviceType 由谁发现的设备    onvif （onvif协议ws广播）   portScanning   (端口扫描)   其他还没有添加
 * */
data class DeviceDisplayInformation(
    var deviceIp: String,               //设备IPv4地址
    var deviceName: String,             //设备名称
    var deviceType: String              //设备的类型
)


// onvif设备
data class OnvifDeviceMsg(
    var username :String,
    var password :String,
    var deviceAddress: String,          // 设备服务地址
    var mediaAddress: String,          // 媒体服务地址
//    var profileToken: List<String>,           //媒体配置文件   配置文件（Profile）的标识符   有多个   可以获取 主码流  与 子码流
    var profileToken: String,           //修改只要 主码流
    var ptzAddress: String,            // ptz服务地址
    var uri:String,                //视频播放的地址
    )


data class PtzData(
    var x : String,
    var y : String,
    var zoom : String
)


/**
 * 返回给UI要执行的操作
 * command内容     DisplayErr 这个是显示错误   WebView   要打开web     PlayVideo  播放视频  StatusCode HTTP状态码
 *
 * */

data class ReturnOperation(
    val command :String,  //返回的是什么操作
    val data:String,   //相关的数据内容
    val playName: Map<String, String>,  //是那个设备的视频在播放中    key 为设备类型   out 是设备的查询列表名
)