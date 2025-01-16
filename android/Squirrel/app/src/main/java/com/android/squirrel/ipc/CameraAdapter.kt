package com.android.squirrel.ipc

import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.android.squirrel.R
import com.android.squirrel.ipc.onvif.DeviceDisplayInformation


class CameraAdapter(
    private var deviceMap: MutableMap<String, DeviceDisplayInformation>,
    private val gestureEventListener: OnGestureEventListener, // 手势回调接口
) : RecyclerView.Adapter<CameraAdapter.CameraViewHolder>() {

    // 获取所有设备的 IP 列表
    private var cameraList = deviceMap.keys.toList()

    // 更新设备数据的方法
    fun updateDeviceData(newDeviceMap: MutableMap<String, DeviceDisplayInformation>) {
        cameraList = newDeviceMap.keys.toList()
        notifyDataSetChanged()  // 通知适配器数据已更新，刷新视图
    }

    // 创建视图持有者
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return CameraViewHolder(itemView)
    }

    // 绑定数据到视图
    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        val deviceIp = cameraList[position]
        val cameraInfo = deviceMap[deviceIp]  // 根据 IP 获取设备信息

        cameraInfo?.let {
            // 设置设备名称、IP 和设备图标
            holder.deviceIp.text = it.deviceIp
            holder.deviceName.text = it.deviceName
            when(it.deviceType){
                "onvif"-> holder.deviceImage.setImageResource( R.drawable.onvif)
                else -> holder.deviceImage.setImageResource( R.drawable.unknown_device)
            }

            // 处理手势事件
            val gestureDetector = GestureDetector(holder.itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val currentPosition = holder.adapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        gestureEventListener.onGestureEvent(currentPosition, "single tapped") // 单击事件
                    }
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val currentPosition = holder.adapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        gestureEventListener.onGestureEvent(currentPosition, "double tapped") // 双击事件
                    }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val currentPosition = holder.adapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        gestureEventListener.onGestureEvent(currentPosition, "long pressed") // 长按事件
                    }
                }
            })

            // 设置触摸监听 viewPager
            holder.itemView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时改变背景色
                        holder.itemView.setBackgroundResource(R.drawable.keyboard_win_buttons_down)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                        viewPager?.setUserInputEnabled(true)
                        // 抬起时恢复原背景色
                        holder.itemView.setBackgroundResource(R.drawable.styles_frame)
                    }
                }
                gestureDetector.onTouchEvent(event)
                true
            }
        }
    }

    // 获取设备数量
    override fun getItemCount(): Int = cameraList.size

    // ViewHolder：存储每一项的视图组件
    class CameraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceImage: ImageView = itemView.findViewById(R.id.deviceImage)
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceIp: TextView = itemView.findViewById(R.id.deviceIp)
    }

    // 手势事件接口
    interface OnGestureEventListener {
        fun onGestureEvent(position: Int, eventType: String)
    }
}
