package com.xiaodong.consulttranscript.core.audio

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xiaodong.consulttranscript.App
import com.xiaodong.consulttranscript.MainActivity
import com.xiaodong.consulttranscript.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 前台服务：让 50–60 分钟的录音在锁屏、切后台、来消息时都不被系统掐断。
 * 通知栏常驻一条可见提示（"正在录音 · 全程不联网"），这也是 Android 的合规要求。
 *
 * ★ 防杀后台三件套（v1.0.3）：
 *   1) 前台服务 + 常驻通知（基础）
 *   2) PARTIAL_WAKE_LOCK 部分唤醒锁：CPU 不深睡，录音循环不会因系统休眠被冻住
 *   3) START_STICKY + RecordingState：被系统杀掉后自动重建，读回 caseId 接着录（断点续录）
 */
class RecorderService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private val recorder = AudioRecorder()

    private var wakeLock: PowerManager.WakeLock? = null
    // 是否由用户主动停止（区分正常结束 vs 系统回收/崩溃）
    @Volatile private var stoppedByUser = false
    @Volatile private var currentCaseId: String? = null

    companion object {
        const val EXTRA_CASE_ID = "caseId"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_STOP = "stop"
        const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> { recorder.pause(); return START_STICKY }
            ACTION_RESUME -> { recorder.resume(); return START_STICKY }
            ACTION_STOP -> {
                stoppedByUser = true
                RecordingState.clear(this)
                recorder.stop()
                releaseWakeLock()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // ★ 断点续录核心：
        //   正常启动 → intent 带 caseId；
        //   服务被系统杀掉后重建 → intent 为 null，从持久化状态读回 caseId。
        val caseId = intent?.getStringExtra(EXTRA_CASE_ID)
            ?: RecordingState.activeCaseId(this)
            ?: return START_NOT_STICKY  // 确实没有进行中的录音，没必要活着

        // 已经有录音协程在跑就别重复启动（服务被重启时可能残留旧 job）
        if (job?.isActive == true) return START_STICKY

        stoppedByUser = false
        currentCaseId = caseId
        // 恢复录音（含续录）时把状态纠正回 RECORDING（数据库写操作放协程）
        scope.launch { (application as App).repository.markRecording(caseId) }
        RecordingState.mark(this, caseId)
        acquireWakeLock()

        // Android 14(API34)+ 起，microphone 型前台服务必须用带 type 的重载，
        // 否则系统会抛 SecurityException / 直接杀服务。这里统一走兼容重载。
        showForeground("正在录音 · 数据仅存本机")

        val target = AudioVault(this).newTempFile(caseId)
        job = scope.launch {
            try {
                // recordTo 内部以「追加」方式写文件，续录时接着上次的 PCM 往后写，
                // 并把已录时长按文件大小折算回来，计时不归零。
                recorder.recordTo(target) { elapsed, _ ->
                    // 每秒报到一次，证明录音还活着；首页据此判断「真录音 vs 假状态」
                    RecordingState.heartbeat(this@RecorderService)
                    showForeground("正在录音 ${formatMs(elapsed)} · 全程不联网")
                }
                // 正常录制流程自然结束（例如录音器自己停了）也按用户停止处理，不留假状态
                if (!stoppedByUser) {
                    currentCaseId?.let { (application as App).repository.markFailedOnRecover(it) }
                    RecordingState.clear(this@RecorderService)
                }
            } catch (t: Throwable) {
                // 麦克风被系统占用、权限途中被撤销等：安静停掉，绝不崩到桌面。
                // 标记为未完成并清状态，避免首页永远卡在「录音中」。
                if (!stoppedByUser) {
                    currentCaseId?.let { (application as App).repository.markFailedOnRecover(it) }
                    RecordingState.clear(this@RecorderService)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    /** 统一的 startForeground 调用：按系统版本选择合规重载 */
    private fun showForeground(text: String) {
        val notification = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            // 通知权限未授予等极端机型：兜住，录音本身照常进行
        }
    }

    /** 申请部分唤醒锁：屏幕可灭，但 CPU 保持运行，保证录音与写文件循环不被冻结 */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${packageName}:recording").apply {
            setReferenceCounted(false)
            // 设 12 小时上限兜底，防止异常情况下永久持锁耗电；正常结束会主动 release
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    override fun onDestroy() {
        // 系统直接回收服务时可能不经过 catch，这里兜底：非用户主动停止 → 标未完成 + 清状态
        if (!stoppedByUser) {
            val cid = currentCaseId
            scope.launch { cid?.let { (application as App).repository.markFailedOnRecover(it) } }
            RecordingState.clear(this)
        }
        recorder.stop()
        job?.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
