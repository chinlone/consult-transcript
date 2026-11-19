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
 * ★ v1.0.6 修复「假录音中」：
 *   光有 caseId 不够——服务被强杀后 caseId 残留，首页会一直显示「录音中」。
 *   增加一个「心跳时间戳」：录音协程每更新一次进度就 heartbeat() 报到一次。
 *   判断是否真在录音 = 有 activeCaseId 且最近 HEARTBEAT_TIMEOUT_MS 内有心跳。
 *   超过这个时间没心跳，就算服务崩了，首页据此把状态纠正为「未完成」。
 */
object RecordingState {
    private const val PREF = "recording_state"
    private const val KEY_ACTIVE_CASE = "active_case_id"
    private const val KEY_HEARTBEAT = "heartbeat_at"

    /** 超过 15 秒没心跳就认为录音已死（正常录音每 1 秒左右报到一次） */
    const val HEARTBEAT_TIMEOUT_MS = 15_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 标记某案例开始录音，并立即打一次心跳 */
    fun mark(ctx: Context, caseId: String) {
        prefs(ctx).edit()
            .putString(KEY_ACTIVE_CASE, caseId)
            .putLong(KEY_HEARTBEAT, System.currentTimeMillis())
            .apply()
    }

    /** 录音协程每次进度回调都调用，证明「我还活着」 */
    fun heartbeat(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_HEARTBEAT, System.currentTimeMillis()).apply()
    }

    /** 当前是否还有未结束的录音；返回 caseId 或 null */
    fun activeCaseId(ctx: Context): String? =
        prefs(ctx).getString(KEY_ACTIVE_CASE, null)?.takeIf { it.isNotBlank() }

    /**
     * 是否真的有一段活跃录音：有 caseId 且心跳新鲜。
     * 首页用它来纠正「假录音中」——心跳过期说明服务已崩，状态该改 FAILED。
     */
    fun isRecordingAlive(ctx: Context): Boolean {
        val id = activeCaseId(ctx) ?: return false
        val last = prefs(ctx).getLong(KEY_HEARTBEAT, 0L)
        return System.currentTimeMillis() - last < HEARTBEAT_TIMEOUT_MS
    }

    /** 录音正常结束（转写/放弃）后清除，避免下次误恢复 */
    fun clear(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_ACTIVE_CASE)
            .remove(KEY_HEARTBEAT)
            .apply()
    }
}
