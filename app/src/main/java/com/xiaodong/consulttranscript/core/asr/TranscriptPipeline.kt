package com.xiaodong.consulttranscript.core.asr

import android.content.Context
import com.xiaodong.consulttranscript.core.audio.AudioVault
import com.xiaodong.consulttranscript.core.diar.SpeakerDiarizer
import com.xiaodong.consulttranscript.core.text.Punctuation
import com.xiaodong.consulttranscript.data.db.CaseEntity
import com.xiaodong.consulttranscript.data.db.SegmentEntity
import com.xiaodong.consulttranscript.data.repo.CaseRepository
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * 转写流水线 —— 全工程的主链路，按需求确认的顺序：
 *
 *   临时音频 → VAD 切段 → 逐段 ASR → 标点 → 写库（每段都存 = 断点续转）
 *   → 全部完成后做说话人分离 → 回填 A/B 标签与低置信度标记
 *   → ★ 音频生命周期决策（默认删 / 同意才留）
 *   → 个案状态改成「已完成」
 *
 * ★ 删除时机特意放在「整篇成稿之后」：
 *   中途退出或失败时临时文件必须留着，下次才能从第 N 段接着跑（断点续转）。
 *   这也是需求阶段跟你讲清楚的那一点。
 */
class TranscriptPipeline(
    private val context: Context,
    private val repo: CaseRepository,
    private val vault: AudioVault,
    private val asr: AsrEngine,
    private val vad: VadSegmenter,
    private val diarizer: SpeakerDiarizer
) {
    data class Progress(val step: Int, val done: Int, val total: Int, val message: String)

    private companion object {
        const val SAMPLE_RATE = 16000
    }

    suspend fun run(caseId: String, onProgress: (Progress) -> Unit) {
        val case = repo.getCase(caseId) ?: error("个案不存在")
        val audio = vault.tempFile(caseId)
        if (!audio.exists()) {
            // 音频已被清掉（例如撤回同意后重跑），直接按已有文字稿收尾
            repo.markDone(caseId)
            return
        }

        asr.load(File(context.filesDir, "models_asr"))
        try {
            val samples = readPcm16k(audio)
            val chunks = vad.split(samples)
            repo.updateCase(case.copy(status = "TRANSCRIBING"))

            // 已完成的段数 = 断点续转的起点
            val existing = repo.segmentsOnce(caseId)
            val startFrom = existing.size

            for (i in startFrom until chunks.size) {
                val c = chunks[i]
                val raw = asr.transcribe(c.samples)
                // ★ 过滤纯标点/噪声段：SenseVoice 常对着静音吐「。」，写进逐字稿就是一堆空句号
                if (Punctuation.isMeaningful(raw)) {
                    val text = Punctuation.restore(raw, c.endMs - c.startMs)
                    repo.insertSegment(
                        SegmentEntity(
                            id = "$caseId-$i", caseId = caseId,
                            startMs = c.startMs, endMs = c.endMs,
                            speaker = "UNKNOWN", text = text,
                            lowConfidence = false, manualOverride = false
                        )
                    )
                }
                onProgress(Progress(1, i + 1, chunks.size, "① 语音转文字 ${i + 1}/${chunks.size} 段"))
            }

            // 说话人分离：对「真正写进库、有文字」的段提声纹，按段顺序一一对齐。
            // 旧代码用 vad 重切一遍再按索引对应，段数一旦因过滤变化就整体错位——已修。
            // 从原始 samples 按每段的 startMs/endMs 精确切片，这样断点续转时前面旧段也在，不会错位。
            onProgress(Progress(2, 0, 1, "② 区分说话人"))
            val finalSegments = repo.segmentsOnce(caseId)
            val chunksForDiar = finalSegments.map { seg ->
                val from = (seg.startMs * SAMPLE_RATE / 1000).toInt().coerceIn(0, samples.size)
                val to = (seg.endMs * SAMPLE_RATE / 1000).toInt().coerceIn(from, samples.size)
                AudioChunk(seg.startMs, seg.endMs, samples.copyOfRange(from, to))
            }
            val assignments = diarizer.assign(chunksForDiar, case.speakerCount)
            finalSegments.forEachIndexed { idx, seg ->
                val a = assignments.getOrNull(idx)
                if (a != null && !seg.manualOverride) {
                    repo.insertSegment(
                        seg.copy(speaker = a.label, lowConfidence = a.lowConfidence)
                    )
                }
            }

            // ★ 唯一的「删 / 留」决策点
            val kept = vault.settleAfterTranscript(caseId, case.consentKeepAudio)
            repo.markDone(caseId, hasAudio = kept != null)

            onProgress(Progress(2, 1, 1, if (kept != null) "已完成 · 已保留录音" else "已完成 · 原始录音已删除"))
        } finally {
            asr.close()
        }
    }

    /** 读 AudioRecorder 写下的 16k/16bit 小端裸 PCM，转成模型要的 float(-1..1) */
    private fun readPcm16k(file: File): FloatArray {
        val bytes = file.readBytes()
        val out = FloatArray(bytes.size / 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in out.indices) out[i] = bb.short.toFloat() / 32768f
        return out
    }
}
