package com.xiaodong.consulttranscript.core.diar

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.xiaodong.consulttranscript.core.asr.AssetCopier
import com.xiaodong.consulttranscript.core.asr.AudioChunk
import java.io.File
import kotlin.math.sqrt

/**
 * 自动区分咨询师／来访者 —— 真实声纹提取（端侧、完全离线）。
 *
 * 流程：
 *   ① 对每个语音段用 SpeakerEmbeddingExtractor 提一段声纹向量
 *      （模型随包放 assets/models/speaker/speaker.onnx，nemo titanet small，约 40MB；
 *       声纹刻画的是音色，与说的语言无关，故英文预训练模型同样可用于中文咨询）
 *   ② 用 K 类 K-Means 聚成 A / B / C / D（k 由新建时选的人数决定，2~4）
 *   ③ 返回每段标签 +「把握大不大」
 *
 * ★ 两个诚实边界（与需求确认一致）：
 *   - 它只知道「有几个人、谁跟谁像」，不知道每个标签对应谁 → 逐字稿页首次逐个映射角色。
 *     人越多越容易分错（音色接近/抢话/孩子离麦远），故每段标签都能单独改。
 *   - 抢话、低声、哽咽、单声道摆中间都会分错 → 每段标签界面可一键改；
 *     把握不大的段标 lowConfidence，界面给浅色底提示复核。
 *
 * API 已按 sherpa-onnx 1.13.8 AAR 的 javap 核对：
 *   SpeakerEmbeddingExtractor(assetManager: AssetManager?, config) —— 传 null 走文件路径
 *   createStream(): OnlineStream / acceptWaveform(float[], int) / inputFinished()
 *   isReady(stream): Boolean / compute(stream): FloatArray
 *   SpeakerEmbeddingExtractorConfig(model, numThreads, debug, provider) —— model 为 val，
 *   只能经构造位置参数传入，故本文件对它用位置参数构造。
 */
class SpeakerDiarizer(private val context: Context) {

    data class Assignment(val label: String /* "A"/"B"/"C"/"D" */, val lowConfidence: Boolean)

    // lazy 持有：1 小时录音会切成上百段，绝不能每段都重新加载一次 40MB 模型
    private var extractor: SpeakerEmbeddingExtractor? = null
    private var extractorAttempted = false

    /**
     * @param requestedK 本次咨询的人数（2~4）。实际簇数不会超过段数——段比人少时没人可分。
     */
    fun assign(chunks: List<AudioChunk>, requestedK: Int = 2): List<Assignment> {
        if (chunks.isEmpty()) return emptyList()
        val vectors = chunks.map { extract(it.samples) }
        val dim = vectors.first().size
        if (dim == 0) return chunks.map { Assignment("A", lowConfidence = true) }

        val k = requestedK.coerceIn(1, chunks.size)
        if (k == 1) return chunks.map { Assignment("A", lowConfidence = false) }

        // 初始化 k 个中心：第一个取向量0，之后每次取「离已选中心最远」的段（k-means++ 思路），
        // 比纯随机稳定，避免两个声音相近的人被分到同一簇。
        val centers = Array(k) { vectors[0] }
        for (c in 1 until k) {
            var far = vectors[0]
            var farD = -1.0f
            for (v in vectors) {
                val nearest = centers.take(c).minOf { distance(v, it) }
                if (nearest > farD) { farD = nearest; far = v }
            }
            centers[c] = far
        }

        val labels = IntArray(chunks.size)
        repeat(20) {
            for (i in vectors.indices) {
                var best = 0; var bestD = Float.MAX_VALUE
                for (c in 0 until k) {
                    val d = distance(vectors[i], centers[c])
                    if (d < bestD) { bestD = d; best = c }
                }
                labels[i] = best
            }
            val sums = Array(k) { FloatArray(dim) }
            val counts = IntArray(k)
            for (i in vectors.indices) {
                val s = sums[labels[i]]; val v = vectors[i]
                for (idx in 0 until minOf(dim, v.size)) s[idx] += v[idx]
                counts[labels[i]]++
            }
            for (c in 0 until k) if (counts[c] > 0) centers[c] = normalize(sums[c], counts[c])
        }

        return vectors.indices.map { i ->
            val dists = centers.map { distance(vectors[i], it) }.sorted()
            // 最近与次近中心差不多远 = 这段不好判；段太短也不好判
            val margin = if (dists.size >= 2)
                kotlin.math.abs(dists[0] - dists[1]) / maxOf(dists[1], 1e-6f) else 1f
            val tooShort = chunks[i].endMs - chunks[i].startMs < 700L
            Assignment(
                label = ('A'.code + labels[i]).toChar().toString(),
                lowConfidence = margin < 0.15f || tooShort
            )
        }
    }

    /** 真实声纹提取；模型缺失或加载失败时回退到频谱粗特征，保证流程不崩 */
    private fun extract(samples: FloatArray): FloatArray {
        val e = ensureExtractor() ?: return fallbackFeature(samples)
        var stream: OnlineStream? = null
        return try {
            stream = e.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            stream.inputFinished()
            // 太短的段可能还没攒够一帧，isReady=false 时用兜底
            if (e.isReady(stream)) e.compute(stream) else fallbackFeature(samples)
        } catch (t: Throwable) {
            fallbackFeature(samples)
        } finally {
            stream?.release()
        }
    }

    private fun ensureExtractor(): SpeakerEmbeddingExtractor? {
        extractor?.let { return it }
        if (extractorAttempted) return null
        extractorAttempted = true
        return try {
            val dir = AssetCopier.copyAssets(context, "models/speaker")
            val modelPath = File(dir, "speaker.onnx").path
            // model 是 val 且无 setter，只能经构造位置参数传；其余取库默认值
            val config = SpeakerEmbeddingExtractorConfig(modelPath, 1, false, "cpu")
            val e = SpeakerEmbeddingExtractor(null, config)
            extractor = e
            e
        } catch (t: Throwable) {
            null
        }
    }

    private fun fallbackFeature(s: FloatArray): FloatArray {
        val buckets = 16
        val out = FloatArray(buckets)
        val step = maxOf(1, s.size / buckets)
        for (b in 0 until buckets) {
            var e = 0f
            val from = b * step
            val to = minOf(s.size, from + step)
            for (i in from until to) e += kotlin.math.abs(s[i])
            out[b] = e / maxOf(1, to - from)
        }
        return normalize(out, 1)
    }

    private fun normalize(v: FloatArray, count: Int): FloatArray {
        val r = FloatArray(v.size) { v[it] / maxOf(1, count) }
        val norm = sqrt(r.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(r.size) { r[it] / norm }
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in 0 until minOf(a.size, b.size)) { val d = a[i] - b[i]; s += d * d }
        return sqrt(s)
    }

    private companion object {
        const val SAMPLE_RATE = 16000
    }
}
