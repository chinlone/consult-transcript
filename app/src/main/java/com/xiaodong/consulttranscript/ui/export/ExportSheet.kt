package com.xiaodong.consulttranscript.ui.export

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaodong.consulttranscript.R
import com.xiaodong.consulttranscript.data.db.CaseEntity
import com.xiaodong.consulttranscript.data.db.SegmentEntity
import com.xiaodong.consulttranscript.export.TxtExporter
import com.xiaodong.consulttranscript.ui.theme.offlineGreen
import kotlinx.coroutines.launch

/**
 * 导出：调系统文件选择器（SAF），让用户自己挑保存位置，App 只写文字。
 * ★ 不会写音频、不会上传到任何地方。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(case: CaseEntity, segments: List<SegmentEntity>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            scope.launch {
                try {
                    TxtExporter.write(ctx, uri, TxtExporter.buildText(case, segments))
                    Toast.makeText(ctx, "已保存到本机", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text(ctx.getString(R.string.export_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(ctx.getString(R.string.export_format))
            Text("文件名：${TxtExporter.suggestedFileName(case)}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(ctx.getString(R.string.export_hint), color = offlineGreen, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { launcher.launch(TxtExporter.suggestedFileName(case)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(ctx.getString(R.string.export_save)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
