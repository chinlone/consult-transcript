package com.xiaodong.consulttranscript

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.xiaodong.consulttranscript.core.audio.RecordingState
import com.xiaodong.consulttranscript.data.db.PlainDbFactory
import com.xiaodong.consulttranscript.data.repo.CaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {

    // 全局单例：数据库只开一次。明文存储，数据安全由使用者自行负责。
    lateinit var repository: CaseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val db = PlainDbFactory.build(this)
        repository = CaseRepository(db, this)

        // 冷启动校正「假录音中」：进程被整个杀掉时服务来不及清状态，
        // 这里把没有活跃心跳、却仍是 RECORDING 的个案改为「未完成」。
        CoroutineScope(Dispatchers.IO).launch {
            val alive = if (RecordingState.isRecordingAlive(this@App))
                RecordingState.activeCaseId(this@App) else null
            if (alive == null) RecordingState.clear(this@App)
            repository.reconcileRecordingStates(alive)
        }
    }

    private fun createNotificationChannel() {
        // 前台服务必须挂一条通知：让用户随时看得见「麦克风正在工作」
        val channel = NotificationChannel(
            CHANNEL_ID,
            "录音与离线转写",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "显示录音/转写进度，不联网、不上传任何内容" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "consult_offline"
    }
}
