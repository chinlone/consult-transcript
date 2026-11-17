package com.xiaodong.consulttranscript.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.xiaodong.consulttranscript.App
import com.xiaodong.consulttranscript.core.asr.*
import com.xiaodong.consulttranscript.core.diar.SpeakerDiarizer

/**
 * 后台转写：点「结束并转写」后把活交给 WorkManager，
 * 用户可以切后台甚至锁屏；1 小时录音慢慢转，不占界面。
 * ★ 全程本地计算，没有任何网络请求（WorkManager 本身也不需要联网）。
 */
class TranscriptionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val caseId = inputData.getString(KEY_CASE_ID) ?: return Result.failure()
        val app = applicationContext as App
        return try {
            val pipeline = TranscriptPipeline(
                context = applicationContext,
                repo = app.repository,
                vault = app.repository.vault,
                asr = SherpaAsrEngine(applicationContext),
                vad = VadSegmenterImpl(applicationContext),
                diarizer = SpeakerDiarizer(applicationContext)
            )
            pipeline.run(caseId) { p ->
                setProgressAsync(
                    Data.Builder()
                        .putInt("step", p.step)
                        .putInt("done", p.done)
                        .putInt("total", p.total)
                        .putString("message", p.message)
                        .build()
                )
            }
            Result.success()
        } catch (e: Exception) {
            // ★ 失败时故意不删临时音频：下次进来还能断点续转
            app.repository.markFailed(caseId)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_CASE_ID = "caseId"
    }
}
