package com.xiaodong.consulttranscript

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.xiaodong.consulttranscript.data.db.PlainDbFactory
import com.xiaodong.consulttranscript.data.repo.CaseRepository

class App : Application() {

    // 全局单例：数据库只开一次。明文存储，数据安全由使用者自行负责。
    lateinit var repository: CaseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val db = PlainDbFactory.build(this)
        repository = CaseRepository(db, this)
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
