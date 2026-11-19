package com.xiaodong.consulttranscript.core.diar

import com.xiaodong.consulttranscript.data.db.CaseEntity
import com.xiaodong.consulttranscript.data.db.SegmentEntity
import org.json.JSONObject

/**
 * 说话人角色标签的显示与映射工具（逐字稿页 / 导出共用）。
 *
 * 数据约定：
 *   · 段落 speaker 存聚类标签 A/B/C/D，或用户单段手动指定的角色名（如「丈夫」）。
 *   · case.speakerLabels 存 {"A":"咨询师","B":"来访者",...}，改一次全篇跟着变。
 *   · case.speakerAMap 是旧版两人物种，仅用于兼容老数据。
 */
object RoleLabels {

    /** 下拉里给的快速预设；用户也可以自己填任意文字。 */
    val PRESETS = listOf("咨询师", "来访者", "丈夫", "妻子", "父亲", "母亲", "孩子", "其他")

    /** 段对应的聚类标签（A/B/C/D） */
    fun clusterLabel(speaker: String): String? =
        speaker.takeIf { it.length == 1 && it[0] in 'A'..'D' }

    /** 把某段的 speaker 解析成给人看的中文名 */
    fun display(speaker: String, case: CaseEntity?): String {
        // 单段手动指定的角色名：直接显示
        if (clusterLabel(speaker) == null && speaker !in setOf("COUNSELOR", "CLIENT", "UNKNOWN", "A", "B", "C", "D")) {
            return speaker
        }
        // 旧版两人物种兼容
        if (speaker == "COUNSELOR") return "咨询师"
        if (speaker == "CLIENT") return "来访者"
        if (speaker in setOf("A", "B") && case?.speakerLabels == null && case?.speakerAMap != null) {
            val aIsCounselor = case.speakerAMap == "COUNSELOR"
            return when (speaker) {
                "A" -> if (aIsCounselor) "咨询师" else "来访者"
                "B" -> if (aIsCounselor) "来访者" else "咨询师"
                else -> speaker
            }
        }
        // 新版：查 JSON 映射
        val map = case?.let { parse(it.speakerLabels) }.orEmpty()
        return map[speaker] ?: "说话人$speaker"
    }

    /** 解析 case.speakerLabels 的 JSON；空/损坏时返回空 map，绝不让界面崩 */
    fun parse(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val out = mutableMapOf<String, String>()
            obj.keys().forEach { key -> out[key] = obj.optString(key) }
            out
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    /**
     * 该个案当前涉及的所有角色名（去重），用于单段编辑下拉。
     * 顺序：已映射的角色 → 稿件里已经用过的自定义名（如「婆婆」「闺蜜」，方便复用）→ 常用预设。
     * 已过滤掉 A/B/C/D 聚类标签和内部保留字，避免把技术标签当成角色显示给用户。
     */
    fun rolesInCase(case: CaseEntity?, segments: List<SegmentEntity> = emptyList()): List<String> {
        if (case == null) return PRESETS
        val map = parse(case.speakerLabels)
        val ordered = ('A'.code until 'A'.code + case.speakerCount)
            .map { it.toChar().toString() }
            .mapNotNull { map[it] }
        val reserved = setOf("COUNSELOR", "CLIENT", "UNKNOWN", "A", "B", "C", "D")
        val custom = segments.map { it.speaker }
            .filter { it.isNotBlank() && clusterLabel(it) == null && it !in reserved }
            .distinct()
        return (ordered + custom + PRESETS).distinct()
    }
}
