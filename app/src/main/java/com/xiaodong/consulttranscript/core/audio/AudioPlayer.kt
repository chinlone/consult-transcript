package com.xiaodong.consulttranscript.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.min

/**
 * 回听（v1.0.6 真正实现）：录音是 16kHz / 16bit / 单声道 的裸 PCM，
 * MediaPlayer 播不了裸流，所以改用 AudioTrack 直接写 PCM 数据。
 *
 * 支持「按时间戳回听某一段」：从 startMs 对应的字节偏移开始读，只播 durationMs 长，
 * 播完或调用 stop() 自动释放。
 */
class AudioPlayer {
    private var track: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BYTES_PER_FRAME = 2 // 16bit 单声道 = 每采样 2 字节
    }

    val isPlaying: Boolean get() = track?.state == AudioTrack.STATE_INITIALIZED &&
            track?.playState == AudioTrack.PLAYSTATE_PLAYING

    /**
     * 播放 pcmFile 中 [startMs, startMs+durationMs) 这一段。
     * @param onEnd 正常播完回调（UI 用来恢复按钮状态），可为空
     */
    fun playSegment(
        pcmFile: File,
        startMs: Long,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        stop()
        if (!pcmFile.exists()) { onEnd?.invoke(); return }

        job = scope.launch {
            try {
                val bufSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(SAMPLE_RATE * BYTES_PER_FRAME) // 至少 1 秒缓冲

                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setSampleRate(SAMPLE_RATE)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track = t
                t.play()

                // 定位到起始字节偏移（时间 ms → 字节）
                val startByte = startMs * SAMPLE_RATE / 1000 * BYTES_PER_FRAME
                val totalBytes = if (durationMs > 0)
                    durationMs * SAMPLE_RATE / 1000 * BYTES_PER_FRAME else Long.MAX_VALUE
                val fileLen = pcmFile.length()
                var remaining = min(totalBytes, fileLen - startByte)

                BufferedInputStream(FileInputStream(pcmFile)).use { input ->
                    if (startByte > 0) input.skip(startByte)
                    val buf = ByteArray(bufSize)
                    while (remaining > 0) {
                        if (job?.isCancelled == true) break
                        val want = min(buf.size.toLong(), remaining).toInt()
                        val read = input.read(buf, 0, want)
                        if (read <= 0) break
                        t.write(buf, 0, read, AudioTrack.WRITE_BLOCKING)
                        remaining -= read
                    }
                }

                // 等缓冲区播放完再释放，避免截断尾音
                val drainTimeout = 2000
                var waited = 0
                while (t.playState == AudioTrack.PLAYSTATE_PLAYING && waited < drainTimeout) {
                    Thread.sleep(50); waited += 50
                }
            } catch (t: Throwable) {
                // 播放失败：静默处理，不让 App 崩
            } finally {
                releaseTrack()
                onEnd?.invoke()
            }
        }
    }

    private fun releaseTrack() {
        track?.let {
            try { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop() } catch (_: Throwable) {}
            it.release()
        }
        track = null
    }

    fun stop() {
        job?.cancel()
        job = null
        releaseTrack()
    }
}
