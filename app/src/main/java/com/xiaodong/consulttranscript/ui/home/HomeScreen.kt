package com.xiaodong.consulttranscript.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaodong.consulttranscript.R
import com.xiaodong.consulttranscript.data.db.CaseEntity
import com.xiaodong.consulttranscript.ui.theme.offlineGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNew: () -> Unit,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val cases by vm.cases.collectAsState()
    val ctx = LocalContext.current
    var pendingDelete by remember { mutableStateOf<CaseEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ctx.getString(R.string.home_title)) },
                actions = {
                    TextButton(onClick = onImport) { Text("导入录音") }
                    TextButton(onClick = onSettings) { Text("设置") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew) { Text(ctx.getString(R.string.home_new)) }
        },
        bottomBar = {
            // 常驻一条安心提示：让用户每次打开都看得见「没联网」
            Surface(tonalElevation = 1.dp) {
                Text(
                    ctx.getString(R.string.offline_badge),
                    color = offlineGreen,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    ) { pad ->
        if (cases.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(ctx.getString(R.string.home_empty))
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(cases, key = { it.id }) { c ->
                    CaseCard(
                        c,
                        onClick = { onOpen(c.id) },
                        onDelete = { pendingDelete = c }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条咨询记录？") },
            text = {
                Text("「${c.alias}」的文字稿和保留的录音将一并永久删除，无法恢复。确定继续吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCase(c)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun CaseCard(c: CaseEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(c.createdAt))
    val status = when (c.status) {
        "RECORDING" -> ctx.getString(R.string.status_recording)
        "TRANSCRIBING" -> ctx.getString(R.string.status_transcribing)
        "FAILED" -> "未完成（可继续）"
        else -> ctx.getString(R.string.status_done)
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(c.alias, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(status, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onDelete, modifier = Modifier.height(32.dp)) {
                        Text("删除", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(date, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(12.dp))
                Text("${c.durationMs / 60000} 分钟", style = MaterialTheme.typography.bodySmall)
                // 「含录音」标记：没勾同意保留的个案不显示
                if (c.hasAudio) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        ctx.getString(R.string.tag_has_audio),
                        color = offlineGreen, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
