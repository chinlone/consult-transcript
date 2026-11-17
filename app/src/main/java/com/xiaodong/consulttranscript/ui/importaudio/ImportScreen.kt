package com.xiaodong.consulttranscript.ui.importaudio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaodong.consulttranscript.ui.theme.offlineGreen

/**
 * 导入录音页：把已有的录音文件（微信/会议/电话录音/录音笔导出的 mp3、m4a、wav 等）
 * 在本机解码成逐字稿。全程不联网，文件不上传，只在手机内处理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit, onFinished: (String) -> Unit) {
    val ctx = LocalContext.current
    val vm: ImportViewModel = viewModel()
    val state by vm.state.collectAsState()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var speakerCount by remember { mutableIntStateOf(2) }
    var keepAudio by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            fileName = uri.toString().substringAfterLast('/').substringBefore('?')
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("导入录音转文字") },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(20.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("把已有的录音文件转成逐字稿", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "支持 mp3 / m4a / wav / aac 等常见格式。文件只在你的手机里处理，不联网、不上传。",
                style = MaterialTheme.typography.bodySmall, color = offlineGreen
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { picker.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state is ImportViewModel.State.Idle
            ) { Text(if (fileName.isEmpty()) "选择录音文件" else "重新选择文件") }

            if (fileName.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("已选：$fileName", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = alias, onValueChange = { alias = it },
                label = { Text("来访者代号") },
                placeholder = { Text("只填代号，不要真实姓名") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                enabled = state is ImportViewModel.State.Idle
            )

            Spacer(Modifier.height(16.dp))
            Text("这次录音里有几个人？", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4).forEach { n ->
                    FilterChip(
                        selected = speakerCount == n,
                        onClick = { speakerCount = n },
                        label = { Text("$n 人") },
                        enabled = state is ImportViewModel.State.Idle
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = keepAudio, onCheckedChange = { keepAudio = it },
                    enabled = state is ImportViewModel.State.Idle)
                Text("在 App 内保留这份录音（默认不保留，源文件仍在你手机里）",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))

            when (val s = state) {
                is ImportViewModel.State.Decoding -> {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ImportViewModel.State.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                else -> {}
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    pickedUri?.let { uri ->
                        vm.startImport(uri, alias, speakerCount, keepAudio, onFinished)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pickedUri != null && state is ImportViewModel.State.Idle
            ) { Text("开始转成文字") }
        }
    }
}
