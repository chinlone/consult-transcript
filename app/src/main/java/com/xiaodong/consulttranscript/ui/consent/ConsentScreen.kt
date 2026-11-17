package com.xiaodong.consulttranscript.ui.consent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.xiaodong.consulttranscript.App
import com.xiaodong.consulttranscript.R
import com.xiaodong.consulttranscript.ui.theme.offlineGreen
import kotlinx.coroutines.launch

/**
 * 知情同意页 —— 需求最后确认的那条「默认不保存录音」就落在这里。
 *
 * 两个独立的勾：
 *  ① 同意本次录音并转写：必需，不勾不能开始（要出稿就必须先录）
 *  ② 同意保留原始录音：★ 默认关闭 ★，只有主动勾了才留文件
 *
 * 界面上明确告诉来访者「成稿后默认自动删除」，并说明保留后可随时删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(onCancel: () -> Unit, onStart: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    var consentBasic by remember { mutableStateOf(false) }
    var consentKeep by remember { mutableStateOf(false) }   // 默认关，不要改成 true
    var alias by remember { mutableStateOf("") }
    var speakerCount by remember { mutableIntStateOf(2) }   // 本次咨询人数，默认两人

    Scaffold(topBar = { TopAppBar(title = { Text(context.getString(R.string.consent_title)) }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            Text(context.getString(R.string.consent_body), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))

            // ① 基础同意
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consentBasic, onCheckedChange = { consentBasic = it })
                Text(context.getString(R.string.consent_basic), Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            // ② 保留录音（默认关闭）
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consentKeep, onCheckedChange = { consentKeep = it })
                        Text(context.getString(R.string.consent_keep), Modifier.weight(1f))
                    }
                    Text(
                        context.getString(R.string.consent_keep_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = offlineGreen
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            // 人数选择：决定自动分离聚成几类。默认两人（咨询师+来访者）。
            Text("本次咨询几个人？", style = MaterialTheme.typography.titleSmall)
            Text(
                "夫妻/家庭咨询选 3 人或 4 人；人越多越可能有分错，逐字稿里都能单独改。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4).forEach { n ->
                    FilterChip(
                        selected = speakerCount == n,
                        onClick = { speakerCount = n },
                        label = { Text("$n 人") }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text(context.getString(R.string.consent_alias)) },
                placeholder = { Text(context.getString(R.string.consent_alias_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "只填代号、不要填真实姓名；导出文件也会用这个代号。",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, Modifier.weight(1f)) {
                    Text(context.getString(R.string.consent_cancel))
                }
                Button(
                    onClick = {
                        scope.launch {
                            // keepAudio 的 true/false 会一路带到 AudioVault.settleAfterTranscript
                            val c = app.repository.createCase(alias.trim(), keepAudio = consentKeep, speakerCount = speakerCount)
                            onStart(c.id)
                        }
                    },
                    enabled = consentBasic,   // 没勾基础同意就不让开始
                    modifier = Modifier.weight(1f)
                ) { Text(context.getString(R.string.consent_start)) }
            }
        }
    }
}
