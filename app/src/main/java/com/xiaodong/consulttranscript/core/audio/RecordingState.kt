package com.xiaodong.consulttranscript.core.audio

import android.content.Context

/**
 * 录音「进行中」状态的轻量持久化（SharedPreferences，纯本机，不联网不加密）。
 *
 * 为什么需要它？
 * 前台服务即使声明 START_STICKY，被系统杀掉重启时传进来的 Intent 是 null，
 * 拿不到 caseId —— 旧代码在这里直接 return，等于「重启了但没恢复录音」。
 * 把当前正在录的 caseId 落盘后，服务重启可以从这里读回来，接着录（断点续录）。
 *
 * 生命周期：
 *   · 真正开始录音时 mark(caseId)
 *   · 用户点「结束并转写」或「放弃」时 clear()
 *   · 服务重启发现还有 activeCaseId → 自动恢复录音
 */
object RecordingState {
    private const val PREF = "recording_state"
    private const val KEY_ACTIVE_CASE = "active_case_id"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 标记某案例开始录音（覆盖旧的，正常场景同一时刻只会有一段录音） */
    fun mark(ctx: Context, caseId: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE_CASE, caseId).apply()
    }

    /** 当前是否还有未结束的录音；返回 caseId 或 null */
    fun activeCaseId(ctx: Context): String? =
        prefs(ctx).getString(KEY_ACTIVE_CASE, null)?.takeIf { it.isNotBlank() }

    /** 录音正常结束（转写/放弃）后清除，避免下次误恢复 */
    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ACTIVE_CASE).apply()
    }
}
