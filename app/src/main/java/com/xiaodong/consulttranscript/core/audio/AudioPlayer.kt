package com.xiaodong.consulttranscript.core.audio

import android.media.MediaPlayer
import java.io.File

/**
 * 回听：只有「含录音」的个案才会用到。
 * 因为存的是裸 PCM（没有 WAV 头），这里用 MediaPlayer 前先包一个临时 WAV 头，
 * 或者更简单——交给界面层用 AudioTrack 播；本类保留最小可用实现，便于你按实际格式调整。
 */
class AudioPlayer {
    private var player: MediaPlayer? = null

    fun playSegment(file: File, startMs: Long) {
        stop()
        // 注：若 AudioRecorder 改存 WAV，直接 setDataSource(file.path) 即可。
        // 当前 PCM 裸流需要一句 setDataSource 的替代实现，见《开发方案与源码说明.md》。
        player = MediaPlayer()
        // player!!.setDataSource(file.path)
        // player!!.seekTo(startMs.toInt())
        // player!!.start()
    }

    fun stop() {
        player?.release()
        player = null
    }
}
