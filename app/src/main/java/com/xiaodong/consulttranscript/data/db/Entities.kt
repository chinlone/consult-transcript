package com.xiaodong.consulttranscript.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 个案（一次咨询）。
 * ★ 只存 alias（来访者代号），不存真实姓名 —— 需求阶段定的合规红线。
 * consentKeepAudio 默认 false：这就是「默认不保存录音」在数据层的落点。
 */
@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey val id: String,
    val alias: String,
    val createdAt: Long,
    val durationMs: Long = 0L,
    /** RECORDING / TRANSCRIBING / DONE / FAILED */
    val status: String = "RECORDING",
    val consentRecorded: Boolean = false,   // 一级同意：同意录音并转写（必需）
    val consentKeepAudio: Boolean = false,  // 二级同意：同意保留原始录音（默认关）
    val hasAudio: Boolean = false,          // 当前是否真的存在保留音频
    /** "COUNSELOR" / "CLIENT" / null（还没指定 A 是谁）—— 旧版两字段，保留兼容历史数据 */
    val speakerAMap: String? = null,
    /** 本次咨询的说话人数（2/3/4），新建时选定，决定自动分离聚成几类 */
    val speakerCount: Int = 2,
    /**
     * 聚类标签 → 角色名 的映射，JSON 形如 {"A":"咨询师","B":"来访者","C":"丈夫"}。
     * 段落只存 A/B/C/D，角色名统一放这里：改一次映射，全篇跟着变（全局对调）；
     * 单段手动指定的角色则写进该段的 speaker 字段，不受全局映射影响（逐句单独改）。
     */
    val speakerLabels: String? = null
)

/** 逐字稿的一段。每段都能单独改文字、改说话人、按时间戳回听。 */
@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(
        entity = CaseEntity::class,
        parentColumns = ["id"],
        childColumns = ["caseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("caseId")]
)
data class SegmentEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val startMs: Long,
    val endMs: Long,
    /** "A" / "B" / "COUNSELOR" / "CLIENT" / "UNKNOWN" */
    val speaker: String,
    val text: String,
    val lowConfidence: Boolean = false,   // 自动判定把握不大 → 界面浅色底提示
    val manualOverride: Boolean = false   // 用户手动改过 → 重跑分离时不覆盖
)
