package com.xiaodong.consulttranscript.ui.record

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.xiaodong.consulttranscript.R
import com.xiaodong.consulttranscript.core.audio.RecorderService
import com.xiaodong.consulttranscript.core.audio.RecordingState
import com.xiaodong.consulttranscript.ui.theme.offlineGreen
import com.xiaodong.consulttranscript.work.TranscriptionWorker
import kotlinx.coroutines.launch

/**
 * 录音页：大计时器、波形、自动分人状态、手动说话人切换（校正兜底）。
 * 「结束并转写」→ 停前台服务 → 把活交给 WorkManager → 跳进度页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    caseId: String,
    requestMicPermission: (onResult: (Boolean) -> Unit) -> Unit,
    onFinished: (String) -> Unit,
    onAbandon: () -> Unit
) {
    val ctx = LocalContext.current
    val vm: RecordViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var speaker by remember { mutableStateOf("咨询师") }
    var permissionDenied by remember { mutableStateOf(false) }
    // 守卫：LaunchedEffect 在重组时可能重跑，绝不能重复 startService
    var serviceStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // ★ 关键修复：先申请权限，拿到「已授予」结果后才启动录音服务。
        // 旧代码是先 launch 权限再立刻 startService，属异步竞态——
        // 首次授权时录音在无权限下启动 → AudioRecord 抛 SecurityException → 闪退。
        requestMicPermission { granted ->
            if (granted && !serviceStarted) {
                serviceStarted = true
                ctx.startService(
                    Intent(ctx, RecorderService::class.java).putExtra(RecorderService.EXTRA_CASE_ID, caseId)
                )
            } else if (!granted) {
                permissionDenied = true
            }
        }
        // 计时器：仅用于界面显示，真实时长以服务里 AudioRecorder 的 elapsedMs 为准
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (!paused && serviceStarted) elapsed += 1000
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("录音中") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (permissionDenied) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("没有麦克风权限，无法录音", color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            permissionDenied = false
                            requestMicPermission { granted ->
                                if (granted && !serviceStarted) {
                                    serviceStarted = true
                                    ctx.startService(
                                        Intent(ctx, RecorderService::class.java)
                                            .putExtra(RecorderService.EXTRA_CASE_ID, caseId)
                                    )
                                } else if (!granted) permissionDenied = true
                            }
                        }) { Text("重新授权") }
                        TextButton(onClick = onAbandon) { Text("放弃本次咨询") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(formatMs(elapsed), style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { ((elapsed % 3000) / 3000f) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text(ctx.getString(R.string.record_auto_diar), color = offlineGreen)

            Spacer(Modifier.height(24.dp))
            Text(ctx.getString(R.string.record_speaker_switch), style = MaterialTheme.typography.labelMedium)
            Row {
                FilterChip(selected = speaker == "咨询师", onClick = { speaker = "咨询师" }, label = { Text("咨询师") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = speaker == "来访者", onClick = { speaker = "来访者" }, label = { Text("来访者") })
            }

            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    paused = !paused
                    ctx.startService(Intent(ctx, RecorderService::class.java)
                        .setAction(if (paused) RecorderService.ACTION_PAUSE else RecorderService.ACTION_RESUME))
                }) { Text(if (paused) "继续" else "暂停") }
                OutlinedButton(onClick = { /* 插时间点标记：写入 segments 的备注，V1 简化为本地列表 */ }) {
                    Text("+ 标记")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = {
                    RecordingState.clear(ctx)
                    ctx.startService(Intent(ctx, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
                    scope.launch { vm.repo.vault.discard(caseId); vm.repo.deleteCase(caseId) }
                    onAbandon()
                }) { Text("放弃") }
                Button(onClick = {
                    RecordingState.clear(ctx)
                    ctx.startService(Intent(ctx, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
                    scope.launch {
                        val c = vm.repo.getCase(caseId)
                        if (c != null) vm.repo.updateCase(c.copy(durationMs = elapsed))
                        // 交给后台任务，用户可切后台/锁屏
                        val req = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                            .setInputData(workDataOf(TranscriptionWorker.KEY_CASE_ID to caseId))
                            .addTag("case_$caseId")   // 进度页按这个 tag 查进度
                            .build()
                        WorkManager.getInstance(ctx).enqueue(req)
                        onFinished(caseId)
                    }
                }, Modifier.weight(1f)) { Text("结束并转写") }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
