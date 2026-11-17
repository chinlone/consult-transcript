package com.xiaodong.consulttranscript.core.audio

import android.content.Context
import java.io.File

/**
 * 音频保险库 —— 管理「录音文件的一生」，对应需求里最关键的一条：
 *
 *   默认不保存录音，除非来访者同意保留录音。
 *
 * 三段式：
 *  ① 临时区 cacheDir/audio_tmp：录音与转写期间存在，中途退出/失败时留着，用来断点续转
 *  ② 成稿之后（不是一停录音就删！）二选一：
 *       没勾「同意保留」→ 覆写删除，只留加密数据库里的文字稿   ← 默认
 *       勾了「同意保留」→ 移到 App 私有加密目录 filesDir/audio_vault，个案标记「含录音」
 *  ③ 来访者事后撤回同意 → 一键删掉已保留的音频，文字稿保留
 *
 * 所有文件都在 App 私有目录里，其他 App 读不到；不申请外部存储权限；不联网。
 */
class AudioVault(private val context: Context) {

    private val tmpDir = File(context.cacheDir, "audio_tmp").apply { mkdirs() }
    private val vaultDir = File(context.filesDir, "audio_vault").apply { mkdirs() }

    /** 开一段新录音：只在临时区建文件，文件名用个案 id，不带来访者真名 */
    fun newTempFile(caseId: String): File = File(tmpDir, "$caseId.pcm")

    fun tempFile(caseId: String): File = File(tmpDir, "$caseId.pcm")

    /**
     * 整篇成稿之后调用。这是「删不删」唯一的决策点。
     * @param keep 来自知情同意页第二个勾选框（默认 false）
     * @return 保留时返回加密目录里的文件，删除时返回 null
     */
    fun settleAfterTranscript(caseId: String, keep: Boolean): File? {
        val tmp = tempFile(caseId)
        if (!keep) {
            SecureDelete.overwriteAndDelete(tmp)
            // 顺手清掉这个人之前可能残留的保留文件（例如撤回同意后再跑一次）
            SecureDelete.overwriteAndDelete(keptFile(caseId))
            return null
        }
        if (!tmp.exists()) return keptFile(caseId).takeIf { it.exists() }
        val dest = keptFile(caseId)
        // 先复制再覆写删原件，避免移动中途失败造成两头都没有
        tmp.copyTo(dest, overwrite = true)
        SecureDelete.overwriteAndDelete(tmp)
        return dest
    }

    fun keptFile(caseId: String): File = File(vaultDir, "$caseId.pcm")

    fun hasKeptAudio(caseId: String): Boolean = keptFile(caseId).exists()

    /** 中途放弃录音/转写：立刻清掉临时文件，不进入成稿流程 */
    fun discard(caseId: String) {
        SecureDelete.overwriteAndDelete(tempFile(caseId))
    }

    /** 来访者撤回同意：只删音频，文字稿不动 */
    fun deleteKept(caseId: String): Boolean = SecureDelete.overwriteAndDelete(keptFile(caseId))

    /** 设置页「删除所有保留的录音」 */
    fun deleteAllKept(): Int {
        var n = 0
        vaultDir.listFiles()?.forEach { if (it.isFile && SecureDelete.overwriteAndDelete(it)) n++ }
        return n
    }

    /**
     * 设置页「清理未完成临时文件」。
     * 只清孤儿：数据库里已标记完成、却还留在临时区的文件；
     * 「转写中」的个案不能被清，否则断点续转就没了。
     */
    fun cleanupOrphans(finishedCaseIds: Set<String>): Int {
        var n = 0
        tmpDir.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension
            if (id in finishedCaseIds && SecureDelete.overwriteAndDelete(f)) n++
        }
        return n
    }
}
