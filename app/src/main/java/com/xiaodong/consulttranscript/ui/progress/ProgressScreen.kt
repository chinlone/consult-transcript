package com.xiaodong.consulttranscript.ui.progress

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.xiaodong.consulttranscript.R
import com.xiaodong.consulttranscript.ui.theme.offlineGreen
import kotlinx.coroutines.delay

/**
 * 进度页：两段式进度「① 语音转文字 → ② 区分说话人」，并常驻「全程不联网」提示。
 * 完成后自动跳逐字稿页（需求文档里写明的跳转）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(caseId: String, onDone: () -> Unit, onBackground: () -> Unit) {
    val ctx = LocalContext.current
    var message by remember { mutableStateOf("准备中…") }
    var fraction by remember { mutableStateOf(0f) }

    LaunchedEffect(caseId) {
        // 简化起见按轮询读取最近一次任务的进度；接好 unique work 后可换成 LiveData 观察
        while (true) {
            delay(1000)
            val infos = WorkManager.getInstance(ctx).getWorkInfosByTag("case_$caseId").get()
            val running = infos.firstOrNull { !it.state.isFinished }
            if (running == null) {
                // 都结束了 → 检查是否成功，成功就进逐字稿
                val all = WorkManager.getInstance(ctx).getWorkInfosByTag("case_$caseId").get()
                if (all.any { it.state == androidx.work.WorkInfo.State.SUCCEEDED }) { onDone(); break }
                continue
            }
            val p = running.progress
            message = p.getString("message") ?: "正在离线转写…"
            val done = p.getInt("done", 0); val total = p.getInt("total", 1)
            fraction = if (total > 0) done / total.toFloat() else 0f
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("正在离线转写") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text(message)
            Spacer(Modifier.height(8.dp))
            Text(ctx.getString(R.string.progress_step_text), style = MaterialTheme.typography.bodySmall)
            Text(ctx.getString(R.string.progress_step_diar), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(32.dp))
            Text(
                ctx.getString(R.string.offline_badge),
                color = offlineGreen, style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBackground) {
                Text(ctx.getString(R.string.progress_background))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "点上面按钮回到首页，转写会在后台继续；完成后可回来查看。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
