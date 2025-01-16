package com.android.squirrel.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.android.squirrel.R


/**
 * 播放按键提示音
 * */
@SuppressLint("WrongConstant")
class PlaySound (context: Context?) {

    private var soundPool: SoundPool? = null
    private var soundId = -1

    init {
        /*支持的声音数量  声音类型  声音质量*/
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(audioAttributes)
            .build()

        /*加载声音资源文件（R.raw.message：音频文件）*/
        soundId = soundPool!!.load(context, R.raw.sound, 1)



    }

    fun play(soundMode: Boolean){
        if (!soundMode) {
            /*播放 声音id 左声道 右声道 优先级  0表示不循环，-1表示循环播放 播放比率，0.5~2，一般为1*/
            soundPool?.play(soundId, 1f, 1f, 0, 0, 1f)
        }
    }

    fun stop() {
        soundPool?.stop(soundId)
    }
    //回收资源
    fun release() {
        if (soundPool != null) {
            soundPool!!.release() //回收SoundPool资源
            soundPool = null
        }
    }

}