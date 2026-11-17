package com.xiaodong.consulttranscript.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max

/**
 * 本地音频导入器：把用户选的 mp3 / m4a / wav / aac 等常见格式，
 * 用安卓系统自带的 MediaExtractor + MediaCodec 解码，再重采样成
 * 16kHz / 单声道 / 16bit PCM，追加写入 AudioVault 的临时文件——
 * 与麦克风录音产出完全相同的格式，故可直接复用现有转写流水线。
 *
 * ★ 全程本地计算，不联网、不上传；不引入任何第三方解码库。
 * ★ 流式处理：1 小时音频也不会一次性读进内存（否则几百 MB 必 OOM）。
 */
class AudioImporter(private val context: Context) {

    data class DecodeProgress(val percent: Int, val message: String)

    /**
     * @return 解码产出 PCM 的时长（毫秒），供个案 durationMs 显示
     */
    suspend fun import(
        uri: Uri,
        target: File,
        onProgress: (DecodeProgress) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var totalOutSamples = 0L
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("这个文件里没有音轨，请换一个录音文件")

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = max(1, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else 0L
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            val resampler = PcmResampler(srcRate, TARGET_RATE)

            // 覆盖写：导入是一次性成型的全新音频，不追加到任何旧内容后
            BufferedOutputStream(FileOutputStream(target, false)).use { out ->
                while (isActive && !sawOutputEos) {
                    if (!sawInputEos) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val input = codec.getInputBuffer(inIndex)!!
                            val n = extractor.readSampleData(input, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, n, pts, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        if (info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val pcm = ByteArray(info.size)
                            outBuf.get(pcm, 0, info.size)
                            // 解码输出为 16bit 小端、按声道交错；转成单声道 float 再重采样
                            val mono = decodeToMono(pcm, channels)
                            totalOutSamples += resampler.feed(mono, out)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                        if (durationUs > 0) {
                            val posUs = extractor.sampleTime.coerceIn(0, durationUs)
                            val pct = (posUs * 100 / durationUs).toInt().coerceIn(0, 100)
                            onProgress(DecodeProgress(pct, "正在解码音频 $pct%"))
                        }
                    }
                    // 其余返回值（INFO_OUTPUT_FORMAT_CHANGED / INFO_TRY_AGAIN_LATER）无需处理，
                    // 采样率与声道已从 extractor 拿到。
                }
                totalOutSamples += resampler.flush(out)
                out.flush()
            }
            totalOutSamples * 1000L / TARGET_RATE
        } finally {
            codec?.let {
                try { it.stop() } catch (_: Throwable) {}
                try { it.release() } catch (_: Throwable) {}
            }
            extractor.release()
        }
    }

    /** 16bit 小端交错的解码输出 → 单声道 float（各声道取平均，归一到 -1..1） */
    private fun decodeToMono(pcm: ByteArray, channels: Int): FloatArray {
        val frames = pcm.size / (2 * channels)
        val out = FloatArray(frames)
        var p = 0
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                val lo = pcm[p++].toInt() and 0xFF
                val hi = pcm[p++].toInt() and 0xFF
                sum += (lo or (hi shl 8)).toShort().toInt()
            }
            out[f] = (sum / channels).toFloat() / 32768f
        }
        return out
    }

    /** 线性插值重采样：任意采样率 → 16k 单声道，流式、跨 buffer 保持连续 */
    private class PcmResampler(private val srcRate: Int, private val dstRate: Int) {
        private val ratio = srcRate.toDouble() / dstRate
        private var inputIndex = -1L      // 已接收的输入样本序号
        private var prev = 0f             // sample[inputIndex-1]
        private var last = 0f             // sample[inputIndex]
        private var produced = 0L         // 已产出的输出样本数

        /** 喂入一批单声道样本，返回写入文件的样本数 */
        fun feed(samples: FloatArray, out: OutputStream): Int {
            var written = 0
            for (s in samples) {
                inputIndex++
                prev = last
                last = s
                while (true) {
                    val pos = produced * ratio
                    val i0 = pos.toLong()
                    // 只产出插值区间已完整落在已知样本内的点
                    if (i0 + 1 > inputIndex) break
                    val frac = (pos - i0).toFloat()
                    val value = prev + (last - prev) * frac
                    writePcm16(out, value)
                    produced++
                    written++
                }
            }
            return written
        }

        /** 收尾：把仍欠的尾部样本用最后一个值补齐 */
        fun flush(out: OutputStream): Int {
            var written = 0
            // 需要补齐的输出数：总输出应为 inputCount/ratio，向上取整一点
            val target = kotlin.math.ceil((inputIndex + 1) / ratio).toLong()
            while (produced < target) {
                writePcm16(out, last)
                produced++
                written++
            }
            return written
        }
    }

    private companion object {
        const val TARGET_RATE = 16000

        fun writePcm16(out: OutputStream, value: Float) {
            val s = (value.coerceIn(-1f, 1f) * 32767f).toInt()
            out.write(s and 0xFF)
            out.write((s shr 8) and 0xFF)
        }
    }
}
