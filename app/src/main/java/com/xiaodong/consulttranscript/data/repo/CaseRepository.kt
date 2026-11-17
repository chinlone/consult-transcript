package com.xiaodong.consulttranscript.data.repo

import android.content.Context
import com.xiaodong.consulttranscript.core.audio.AudioVault
import com.xiaodong.consulttranscript.data.db.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.UUID

/** 仓库层：界面只跟它打交道，不直接碰数据库和文件。 */
class CaseRepository(private val db: AppDatabase, private val context: Context) {

    val vault = AudioVault(context)

    fun observeCases(): Flow<List<CaseEntity>> = db.caseDao().observeAll()
    fun observeCase(id: String): Flow<CaseEntity?> = db.caseDao().observe(id)
    fun observeSegments(caseId: String): Flow<List<SegmentEntity>> = db.segmentDao().observe(caseId)

    suspend fun getCase(id: String) = db.caseDao().get(id)
    suspend fun segmentsOnce(caseId: String) = db.segmentDao().once(caseId)
    suspend fun insertSegment(s: SegmentEntity) = db.segmentDao().upsert(s)
    suspend fun updateCase(c: CaseEntity) = db.caseDao().upsert(c)

    /** 放弃录音：删掉空个案（segments 有外键级联，一并清掉） */
    suspend fun deleteCase(id: String) {
        db.segmentDao().deleteByCase(id)
        db.caseDao().delete(id)
    }

    /** 新建个案：id 用随机 UUID，绝不用姓名或手机号之类的可识别信息 */
    suspend fun createCase(alias: String, keepAudio: Boolean, speakerCount: Int = 2): CaseEntity {
        val c = CaseEntity(
            id = UUID.randomUUID().toString(),
            alias = alias.ifBlank { "未命名" },
            createdAt = System.currentTimeMillis(),
            consentRecorded = true,
            consentKeepAudio = keepAudio,
            speakerCount = speakerCount.coerceIn(2, 4)
        )
        db.caseDao().upsert(c)
        return c
    }

    suspend fun markDone(caseId: String, hasAudio: Boolean = false) {
        val c = getCase(caseId) ?: return
        updateCase(c.copy(status = "DONE", hasAudio = hasAudio))
    }

    suspend fun markFailed(caseId: String) {
        val c = getCase(caseId) ?: return
        updateCase(c.copy(status = "FAILED"))
    }

    /** 用户手动改说话人标签：标 manualOverride，重跑自动分离时不覆盖 */
    suspend fun overrideSpeaker(segment: SegmentEntity, speaker: String) {
        insertSegment(segment.copy(speaker = speaker, manualOverride = true, lowConfidence = false))
    }

    suspend fun editText(segment: SegmentEntity, text: String) {
        insertSegment(segment.copy(text = text, manualOverride = true))
    }

    /** 第一次打开逐字稿时指定「A 是谁」（旧版两人物种保留兼容） */
    suspend fun setSpeakerAMap(caseId: String, role: String) {
        val c = getCase(caseId) ?: return
        updateCase(c.copy(speakerAMap = role))
    }

    /** 保存聚类标签→角色名的映射，例如 A→咨询师 B→来访者 C→丈夫。改一次全篇跟着变。 */
    suspend fun setSpeakerLabels(caseId: String, labels: Map<String, String>) {
        val c = getCase(caseId) ?: return
        val json = JSONObject().apply { labels.forEach { (k, v) -> put(k, v) } }.toString()
        updateCase(c.copy(speakerLabels = json, speakerAMap = null))
    }

    /**
     * 全局对调：把段落里所有等于 fromLabel 的聚类标签换成 toLabel（用于两个人叫反了）。
     * 只动「还是原始标签、没被单段手动指定过角色名」的段——manualOverride 的段不碰。
     */
    suspend fun relabelSegments(caseId: String, fromLabel: String, toLabel: String) {
        db.segmentDao().once(caseId)
            .filter { !it.manualOverride && it.speaker == fromLabel }
            .forEach { seg -> insertSegment(seg.copy(speaker = toLabel)) }
    }

    /** 撤回同意：只删音频，文字稿保留 */
    suspend fun deleteKeptAudio(caseId: String) {
        vault.deleteKept(caseId)
        val c = getCase(caseId) ?: return
        updateCase(c.copy(hasAudio = false, consentKeepAudio = false))
    }

    suspend fun deleteAllKeptAudio(): Int {
        val n = vault.deleteAllKept()
        // 同步把数据库里的标记清掉
        db.caseDao().let { dao ->
            dao.finishedIds().forEach { id ->
                getCase(id)?.let { c -> if (c.hasAudio) updateCase(c.copy(hasAudio = false)) }
            }
        }
        return n
    }

    suspend fun cleanupOrphans(): Int {
        val finished = db.caseDao().finishedIds().toSet()
        return vault.cleanupOrphans(finished)
    }

    /** 彻底清空：数据库 + 所有音频（含临时区）。二次确认后才会调用。 */
    suspend fun clearAll() {
        db.segmentDao().deleteAll()
        db.caseDao().deleteAll()
        vault.deleteAllKept()
        context.cacheDir.resolve("audio_tmp").listFiles()?.forEach { it.delete() }
    }
}
