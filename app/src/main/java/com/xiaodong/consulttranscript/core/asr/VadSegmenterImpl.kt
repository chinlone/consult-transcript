package com.xiaodong.consulttranscript.core.asr

import android.content.Context
import kotlin.math.max

/**
 * 断句：把 1 小时录音按静音切成小段再逐段识别。
 * 好处有三：内存不炸、能显示逐段进度、中途退出能断点续转。
 *
 * 这里给了一个「不依赖 VAD 模型也能跑」的兜底：按能量阈值 + 最长 20 秒硬切。
 * 接好官方 Silero/FSMN VAD 后，把 split() 换成 AAR 的实现即可，界面无感。
 */
class VadSegmenterImpl(private val context: Context) : VadSegmenter {

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_CHUNK_MS = 20_000L      // 单段最长 20 秒
        const val SILENCE_MS = 500L           // 连续静音 500ms 就切一刀
        const val ENERGY_THRESHOLD = 0.008f  // 能量阈值，实测时按咨询室底噪微调
    }

    override fun split(samples: FloatArray): List<AudioChunk> {
        val result = mutableListOf<AudioChunk>()
        var start = 0
        var silenceRun = 0
        var i = 0
        while (i < samples.size) {
            val windowEnd = minOf(samples.size, i + 320) // 20ms 一窗
            var energy = 0f
            for (k in i until windowEnd) energy += kotlin.math.abs(samples[k])
            energy /= (windowEnd - i)
            if (energy < ENERGY_THRESHOLD) silenceRun += 20 else silenceRun = 0

            val elapsedMs = (i - start) * 1000L / SAMPLE_RATE
            val shouldCut = (silenceRun >= SILENCE_MS && elapsedMs > 800L) || elapsedMs >= MAX_CHUNK_MS
            if (shouldCut) {
                val end = minOf(samples.size, i + 320)
                if (end - start > SAMPLE_RATE / 5) { // 丢掉太短的碎片
                    result += AudioChunk(
                        startMs = start * 1000L / SAMPLE_RATE,
                        endMs = end * 1000L / SAMPLE_RATE,
                        samples = samples.copyOfRange(start, end)
                    )
                }
                start = end
                silenceRun = 0
                i = end
            } else {
                i = windowEnd
            }
        }
        if (start < samples.size) {
            result += AudioChunk(
                startMs = start * 1000L / SAMPLE_RATE,
                endMs = samples.size * 1000L / SAMPLE_RATE,
                samples = samples.copyOfRange(start, samples.size)
            )
        }
        return result
    }
}
