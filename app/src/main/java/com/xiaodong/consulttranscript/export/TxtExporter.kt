package com.xiaodong.consulttranscript.export

import android.content.Context
import android.net.Uri
import com.xiaodong.consulttranscript.core.diar.RoleLabels
import com.xiaodong.consulttranscript.data.db.CaseEntity
import com.xiaodong.consulttranscript.data.db.SegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 导出纯文字 TXT，通过系统文件选择器（SAF）写到用户自己挑的位置。
 * ★ 只读文字表，绝不碰音频文件；全程不经任何服务器。
 */
object TxtExporter {

    fun suggestedFileName(case: CaseEntity): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date(case.createdAt))
        // 过滤掉文件名非法字符；代号由用户填，这里再兜一层
        val safe = case.alias.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(24)
        return "${safe}_$date.txt"
    }

    fun buildText(case: CaseEntity, segments: List<SegmentEntity>): String {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(case.createdAt))
        sb.appendLine("咨询逐字稿")
        sb.appendLine("来访者代号：${case.alias}")
        sb.appendLine("日期：$date")
        sb.appendLine("说明：本机离线转写，文字稿已人工校对；原始录音${if (case.hasAudio) "经同意保留" else "未保留（成稿后已删除）"}")
        sb.appendLine("————————————————————")
        for (s in segments) {
            val who = RoleLabels.display(s.speaker, case)
            sb.appendLine("[${formatMs(s.startMs)}] $who：${s.text}")
        }
        return sb.toString()
    }

    suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(text.toByteArray(Charsets.UTF_8))
            os.flush()
        } ?: throw IllegalStateException("无法打开导出位置，请换一个目录重试")
    }

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }
}
