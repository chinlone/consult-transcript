package com.xiaodong.consulttranscript.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaodong.consulttranscript.App
import com.xiaodong.consulttranscript.R
import com.xiaodong.consulttranscript.ui.theme.offlineGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteAudio by remember { mutableStateOf(false) }
    var modelInfo by remember { mutableStateOf("读取中…") }

    // 电池优化白名单状态：绿色=已加入（录音最稳），橙色=未加入（可能被系统限制后台）
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    var batteryIgnored by remember {
        mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName))
    }

    LaunchedEffect(Unit) {
        // 直接读 assets 里的清单，不联网
        modelInfo = try {
            ctx.assets.open("model_manifest.json").bufferedReader().use { it.readText() }
                .lineSequence().firstOrNull { it.contains("total_size_hint") }
                ?.trim() ?: "模型已随包内置"
        } catch (e: Exception) { "模型清单未找到，请检查 assets/models/" }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(ctx.getString(R.string.settings_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text(ctx.getString(R.string.offline_badge), color = offlineGreen)
            Spacer(Modifier.height(16.dp))

            Text(ctx.getString(R.string.settings_model), style = MaterialTheme.typography.titleSmall)
            Text(modelInfo, style = MaterialTheme.typography.bodySmall)
            Text("三个模型都随安装包内置，首次启动不下载任何东西。", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            Text("本机自用版 · 数据以明文存储在本机，安全由使用者自行负责", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))

            // ---- 后台保活设置：长时录音不被系统杀掉的关键 ----
            Text("后台保活", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                if (batteryIgnored) "✓ 已加入电池优化白名单，录音最稳定"
                else "⚠ 尚未加入电池优化白名单，长时间录音可能被系统中断",
                color = if (batteryIgnored) offlineGreen else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                try {
                    // 直接拉起「忽略电池优化」授权页，用户在系统弹窗里点允许即可
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${ctx.packageName}"))
                    )
                } catch (e: Exception) {
                    // 个别机型不支持直接授权，退而求其次打开电池优化列表让用户自己找
                    try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    catch (e2: Exception) { Toast.makeText(ctx, "请在系统设置里手动关闭本 App 的电池优化", Toast.LENGTH_LONG).show() }
                }
            }, Modifier.fillMaxWidth()) { Text(if (batteryIgnored) "已设置（点此查看）" else "一键加入白名单") }
            Text(
                "华为/小米/OPPO/vivo 等机型，还需在「设置→应用→自启动/后台」里允许本 App 后台运行。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                // 从系统设置页返回后刷新状态
                batteryIgnored = pm.isIgnoringBatteryOptimizations(ctx.packageName)
            }, Modifier.fillMaxWidth()) { Text("刷新保活状态") }
            Spacer(Modifier.height(24.dp))

            Button(onClick = { confirmDeleteAudio = true }, Modifier.fillMaxWidth()) {
                Text(ctx.getString(R.string.settings_delete_all_audio))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val n = app.repository.cleanupOrphans()
                    Toast.makeText(ctx, "已清理 $n 个临时文件", Toast.LENGTH_SHORT).show()
                }
            }, Modifier.fillMaxWidth()) { Text(ctx.getString(R.string.settings_cleanup_tmp)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmClear = true }, Modifier.fillMaxWidth()) {
                Text(ctx.getString(R.string.settings_clear_all), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDeleteAudio) {
        ConfirmDialog(
            title = "删除所有保留的录音？",
            body = "只删音频，文字逐字稿全部保留。删除后这些个案将不能回听，且无法恢复。",
            onConfirm = {
                scope.launch {
                    val n = app.repository.deleteAllKeptAudio()
                    Toast.makeText(ctx, "已删除 $n 个录音文件", Toast.LENGTH_SHORT).show()
                }
                confirmDeleteAudio = false
            },
            onDismiss = { confirmDeleteAudio = false }
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "清空全部数据？",
            body = "所有个案、逐字稿、保留的录音都会被删除，无法恢复。请确认已做好导出备份。",
            onConfirm = { scope.launch { app.repository.clearAll() }; confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
