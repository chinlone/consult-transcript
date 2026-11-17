package com.xiaodong.consulttranscript.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 录音器：固定 16kHz / 单声道 / 16bit PCM。
 * 为什么是这个格式？端侧语音识别模型都按 16k 单声道训练，格式对不上会识别崩。
 *
 * 数据直接写进 AudioVault 给的临时文件（不落外部存储、不进数据库）。
 * 支持暂停/继续，暂停时不写数据，但计时和文件句柄保持。
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
    }

    @Volatile private var recording = false
    @Volatile private var paused = false
    private var audioRecord: AudioRecord? = null

    /** 已录的毫秒数，给界面上的大计时器用 */
    @Volatile var elapsedMs: Long = 0L
        private set

    /**
     * 阻塞式录制，必须在前台服务的协程里调用（不能占主线程）。
     * @param onElapsed 每 200ms 回调一次，用来刷新界面计时器与音量波形
     */
    @SuppressLint("MissingPermission") // 权限在进录音页之前已经申请
    suspend fun recordTo(target: File, onElapsed: (Long, Float) -> Unit) = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, SAMPLE_RATE * 2) // 至少 1 秒缓冲，防止低端机 underrun
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        audioRecord = recorder
        val buffer = ShortArray(bufSize / 2)
        recording = true
        // ★ 断点续录：文件以「追加」方式写入。若服务曾被系统杀掉后重启，
        //   目标文件已有上一段 PCM，这里按字节数折算已录时长，界面计时不归零。
        //   16bit 单声道 → 每秒 2 × 16000 字节。
        elapsedMs = if (target.exists()) target.length() * 1000L / (2L * SAMPLE_RATE) else 0L

        BufferedOutputStream(FileOutputStream(target, true)).use { out ->
            recorder.startRecording()
            var sinceCallback = 0L
            while (isActive && recording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                if (paused) continue // 暂停：丢掉这段，计时也不涨

                // 写原始 PCM（小端），转写阶段由 VadSegmenter 直接按 short 读
                val bytes = ByteArray(read * 2)
                for (i in 0 until read) {
                    val s = buffer[i].toInt()
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                out.write(bytes)

                val deltaMs = read * 1000L / SAMPLE_RATE
                elapsedMs += deltaMs
                sinceCallback += deltaMs
                if (sinceCallback >= 200) {
                    // 简单算个音量（0..1）给波形用，不做任何上传
                    var peak = 0
                    for (i in 0 until read) peak = maxOf(peak, kotlin.math.abs(buffer[i].toInt()))
                    onElapsed(elapsedMs, (peak / 32768f).coerceIn(0f, 1f))
                    sinceCallback = 0
                }
            }
            out.flush()
        }
        recorder.stop()
        recorder.release()
        audioRecord = null
    }

    fun pause() { paused = true }
    fun resume() { paused = false }
    fun stop() { recording = false }
}
