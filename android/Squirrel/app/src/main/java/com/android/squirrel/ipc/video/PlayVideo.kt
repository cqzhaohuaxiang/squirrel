package com.android.squirrel.ipc.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.android.squirrel.R
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

/***这个是播放视频相关 Jetpack Media3  要在main线程中运行**/
class PlayVideo(private val context: Context,playerView: TextureView) : ViewModel(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var gravitySensor: Sensor? = null
    private var exoPlayer: ExoPlayer? = null
    private var isVideoReady = false
    private var videoWidth = 0f
    private var videoHeight = 0f
    private val _degrees = MutableLiveData<FloatArray>() // 更新屏幕显示方向
    val degrees: LiveData<FloatArray> get() = _degrees

    init {
        initPlayer(playerView)
        initSensor()
    }

    private fun initSensor(){

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        sensorManager?.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI)
    }

    @OptIn(UnstableApi::class)
    private  fun initPlayer(playerView: TextureView){

        // 自定义 LoadControl 配置，减少缓冲时间
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                300,   // 最小缓冲时间（毫秒），尽量小，快速开始
                1000,  // 最大缓冲时间（毫秒），稍大一点以避免频繁缓冲
                300,   // 播放时所需的最小缓冲量（毫秒），尽量小
                300    // 缓冲恢复后的播放最小缓冲量（毫秒），尽量小
            )
            .build()

        /**
         * EXTENSION_RENDERER_MODE_OFF: 当你希望最大化兼容性、减少初始化时间，或者仅仅希望使用标准渲染器时。
         * EXTENSION_RENDERER_MODE_ON: 当你希望启用扩展渲染器支持更多格式，但仍然希望优先使用核心渲染器时。
         * EXTENSION_RENDERER_MODE_PREFER: 当你明确知道扩展渲染器（例如硬件解码器或某些视频编码格式的解码器）能够提供更好性能时，优先使用扩展渲染器。
         * **/
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // 初始化 ExoPlayer
        exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
//            .setDetachSurfaceTimeoutMs(1000) // 设置从 Surface 分离的超时时间
            .build()

        playerView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                // 当 SurfaceTexture 可用时被调用，创建 Surface 并绑定到 ExoPlayer
                val videoSurface = Surface(surface)
                exoPlayer?.setVideoSurface(videoSurface)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                // 当 SurfaceTexture 的大小发生变化时被调用，可以根据需要调整视频大小或布局
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                // 当 SurfaceTexture 被销毁时被调用，释放资源
                exoPlayer?.setVideoSurface(null)
                return true // 返回 true 表示我们已经销毁了 SurfaceTexture，系统可以继续处理
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                // 当 SurfaceTexture 更新时被调用，你可以在这里做一些处理，例如每帧的渲染等
            }
        }

        exoPlayer?.addListener(object : Player.Listener {
            //视频大小改变时回调
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // 获取视频的宽高
                videoWidth = videoSize.width.toFloat()
                videoHeight = videoSize.height.toFloat()
            }
                //视频状态改变回调
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
//                when (playbackState) {
//                    Player.STATE_IDLE -> {
//                        // 播放器处于空闲状态
//                        Log.d("ExoPlayer", "Player is idle")
//                    }
//                    Player.STATE_BUFFERING -> {
//                        // 播放器正在缓冲
//                        Log.d("ExoPlayer", "Player is buffering")
//                    }
//                    Player.STATE_READY -> {
//                        // 播放器已准备好，可以播放
//                        Log.d("ExoPlayer", "Player is ready")
//                    }
//                    Player.STATE_ENDED -> {
//                        // 播放已结束
//                        Log.d("ExoPlayer", "Playback ended")
//                    }
//                }
                if (playbackState == Player.STATE_ENDED) {
                    noVideoPlaying(context)
                    //通知UI 小窗口
                    _degrees.value = floatArrayOf(0f, videoWidth/videoHeight)
                }
            }

            // 捕获到播放器错误
            override fun onPlayerError(error: PlaybackException) {
                noVideoPlaying(context)
                //通知UI 小窗口
                _degrees.value = floatArrayOf(0f, videoWidth/videoHeight)
            }
        })

        noVideoPlaying(context)
    }

    @OptIn(UnstableApi::class)
    private  fun noVideoPlaying(context: Context){
        exoPlayer?.stop()
        val mediaItem = MediaItem.fromUri("android.resource://com.android.squirrel/" + R.raw.no_video)
        val mediaSource = DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
        // 将媒体源添加到 ExoPlayer
        exoPlayer?.setMediaSource(mediaSource)
        exoPlayer?.prepare()  // 确保播放器已准备好，避免重复初始化
        exoPlayer?.playWhenReady = true// 播放
        exoPlayer?.repeatMode = ExoPlayer.REPEAT_MODE_ONE // 循环播放
        exoPlayer?.volume = 0f  // 设置音量为静音
        isVideoReady = false

    }
    // 重试播放
    private fun retryPlaying() {
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    @OptIn(UnstableApi::class)
     fun playUri( uri: String) {

        if (uri.isNotEmpty()) {
            exoPlayer?.stop()

            val mediaItem = MediaItem.fromUri(uri)
            // 创建媒体源并准备播放
            val mediaSource = DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
            exoPlayer?.setMediaSource(mediaSource)
            exoPlayer?.prepare()  // 确保播放器已准备好，避免重复初始化
            exoPlayer?.playWhenReady = true// 播放
            // 设置播放器控制
            exoPlayer?.repeatMode = ExoPlayer.REPEAT_MODE_OFF // 不循环播放
            exoPlayer?.volume = 1f
            isVideoReady = true

        }
    }


    override fun onSensorChanged(event: SensorEvent?) {
        // 确保事件不为空，且是重力传感器的事件
        if (event == null || event.sensor.type != Sensor.TYPE_GRAVITY) return
        // 获取传感器的值
        val gravity = event.values

        if (gravity != null) {
            // 获取x, y, z轴的加速度值
            val x = gravity[0]
            val y = gravity[1]
            val z = gravity[2]
            // 计算每个轴的绝对值
            val absX = abs(x)
            val absY = abs(y)
            val absZ = abs(z)

            // 设置一个阈值，避免微小的振动导致误判
            val threshold = 0.5f

            // 如果 x, y, z 的值都小于阈值，可能是因为设备在静止或者平放不动
            if (absX < threshold && absY < threshold && absZ < threshold) {
                return
            }

            // 判断哪个轴的绝对值最大，从而判断设备方向
             val degrees = when {
                absY > absX && absY > absZ -> {
                    if (y > 0)  0f else  180f
                }
                absX > absY && absX > absZ -> {
                    if (x > 0)  90f else  270f
                }
                // 其他情况下
                else -> 0f
            }

            if (isVideoReady)_degrees.value = floatArrayOf(degrees, videoWidth/videoHeight)

        }
    }
    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    fun unInit(){
        sensorManager?.unregisterListener(this)
        exoPlayer?.release()//销毁时释放 ExoPlayer
    }
}