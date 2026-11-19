package com.xiaodong.consulttranscript.ui.transcript

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaodong.consulttranscript.R
import com.xiaodong.consulttranscript.core.diar.RoleLabels
import com.xiaodong.consulttranscript.data.db.CaseEntity
import com.xiaodong.consulttranscript.data.db.SegmentEntity
import com.xiaodong.consulttranscript.ui.export.ExportSheet
import com.xiaodong.consulttranscript.ui.theme.offlineGreen

/**
 * 核心页：分段逐字稿。
 *  - 第一次进来弹「每个说话人是谁」→ 逐个指定一次，全篇套用
 *  - 之后顶部「角色对应」按钮可随时重开；两个人叫反了可一键对调
 *  - 点段落 → 改错字 / 改这一段说话人（个别句子单独改，不影响全篇）
 *  - 低置信度段落浅色底提示复核
 *  - 只有「含录音」的个案才显示回听和删除保留录音
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(caseId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val vm: TranscriptViewModel = viewModel()
    LaunchedEffect(caseId) { vm.bind(caseId) }
    val case by vm.case.collectAsState()
    val segments by vm.segments.collectAsState()
    val playing by vm.playing.collectAsState()

    var showExport by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SegmentEntity?>(null) }
    var confirmDeleteAudio by remember { mutableStateOf(false) }
    var showRoleMap by remember { mutableStateOf(false) }
    // 用户按返回键暂时关掉映射弹窗：本次进入不再自动弹，但顶部按钮随时能开
    var mapDismissed by remember { mutableStateOf(false) }

    val needsMap = case != null && case!!.speakerLabels == null &&
        case!!.speakerAMap == null && segments.isNotEmpty()

    LaunchedEffect(needsMap) {
        if (needsMap && !mapDismissed) showRoleMap = true
    }

    if (showRoleMap && case != null) {
        RoleMapDialog(
            case = case!!,
            onSave = { labels ->
                vm.setSpeakerLabels(labels)
                showRoleMap = false
                mapDismissed = true
            },
            onSwap = { from, to -> vm.swapLabels(from, to) },
            onDismiss = { showRoleMap = false; mapDismissed = true }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(case?.alias ?: "逐字稿") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = { showRoleMap = true }) { Text("角色对应") }
                    TextButton(onClick = { showExport = true }) { Text("导出") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val audioText = if (case?.hasAudio == true)
                ctx.getString(R.string.transcript_has_audio) else ctx.getString(R.string.transcript_no_audio)
            Text(
                audioText, color = offlineGreen, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (case?.hasAudio == true) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "点击下方每段左侧的「▶ 回听」可听这一段录音",
                        style = MaterialTheme.typography.labelSmall, color = offlineGreen
                    )
                    TextButton(onClick = { confirmDeleteAudio = true }) {
                        Text(ctx.getString(R.string.transcript_delete_audio))
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(segments, key = { it.id }) { seg ->
                    SegmentRow(
                        seg = seg,
                        case = case,
                        canPlay = case?.hasAudio == true,
                        isPlaying = playing?.id == seg.id,
                        onPlay = { vm.togglePlay(seg) },
                        onClick = { editing = seg }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    editing?.let { seg ->
        EditSegmentDialog(
            seg = seg,
            case = case,
            segments = segments,
            onDismiss = { editing = null },
            onSaveText = { t -> vm.editText(seg, t); editing = null },
            onPickSpeaker = { s -> vm.changeSpeaker(seg, s); editing = null }
        )
    }

    if (confirmDeleteAudio) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAudio = false },
            title = { Text("删除保留的录音？") },
            text = { Text("只删除音频文件，文字逐字稿会保留。删除后无法回听，且不能恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteKeptAudio(); confirmDeleteAudio = false }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAudio = false }) { Text("取消") } }
        )
    }

    if (showExport && case != null) {
        ExportSheet(case = case!!, segments = segments, onDismiss = { showExport = false })
    }
}

/**
 * 逐个说话人指定角色。改完保存 → 写进 case.speakerLabels → 全篇所有该标签的段都变。
 * 两个人叫反了：点「一键对调」交换两标签的角色与段落标签；单段手动改过的段不受影响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleMapDialog(
    case: CaseEntity,
    onSave: (Map<String, String>) -> Unit,
    onSwap: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val labels = ('A'.code until 'A'.code + case.speakerCount).map { it.toChar().toString() }
    val existing = RoleLabels.parse(case.speakerLabels).toMutableMap()
    // 旧数据兜底：从 speakerAMap 推出 A/B 的初始角色
    if (existing.isEmpty() && case.speakerAMap != null) {
        val aIsC = case.speakerAMap == "COUNSELOR"
        if (labels.contains("A")) existing["A"] = if (aIsC) "咨询师" else "来访者"
        if (labels.contains("B")) existing["B"] = if (aIsC) "来访者" else "咨询师"
    }
    val draft = remember { mutableStateMapOf<String, String>().apply { putAll(existing) } }
    var focused by remember { mutableStateOf(labels.firstOrNull() ?: "A") }
    // 点「其他」时展开自定义输入，填好后再写进该说话人，避免把字面量「其他」当成角色
    var customFor by remember { mutableStateOf<String?>(null) }
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每个人是谁？") },
        text = {
            Column {
                Text(
                    "给每个说话人指定角色。点选后整篇逐字稿都会按这个显示；以后想改，点右上角「角色对应」重开。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                labels.forEach { label ->
                    OutlinedTextField(
                        value = draft[label] ?: "",
                        onValueChange = { draft[label] = it; focused = label },
                        label = { Text("说话人 $label") },
                        placeholder = { Text("如：咨询师 / 丈夫 / 母亲") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("常用角色（点一下填给「说话人 $focused」）：", style = MaterialTheme.typography.labelSmall)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    RoleLabels.PRESETS.forEach { role ->
                        AssistChip(
                            onClick = {
                                if (role == "其他") {
                                    customFor = focused
                                    customText = ""
                                } else {
                                    draft[focused] = role
                                }
                            },
                            label = { Text(role) }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                if (customFor != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("给「说话人 $customFor」自定义角色名：", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("角色名") },
                        placeholder = { Text("如：婆婆 / 闺蜜 / 前夫") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        TextButton(onClick = {
                            val name = customText.trim()
                            if (name.isNotBlank()) draft[customFor!!] = name
                            customFor = null
                        }) { Text("填入") }
                        TextButton(onClick = { customFor = null }) { Text("取消") }
                    }
                }
                if (case.speakerCount >= 2) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { onSwap(labels[0], labels[1]) }) {
                        Text("两个人叫反了？一键对调 A / B")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(labels.associateWith { draft[it]?.takeIf { s -> s.isNotBlank() } ?: "说话人$it" })
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后再说") } }
    )
}

@Composable
private fun SegmentRow(
    seg: SegmentEntity,
    case: CaseEntity?,
    canPlay: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onClick: () -> Unit
) {
    val who = RoleLabels.display(seg.speaker, case)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .then(if (seg.lowConfidence) Modifier.background(Color(0xFFFFF3CD)) else Modifier)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (canPlay) {
                        TextButton(onClick = onPlay, modifier = Modifier.height(30.dp)) {
                            Text(if (isPlaying) "■ 停止" else "▶ 回听",
                                style = MaterialTheme.typography.labelSmall,
                                color = offlineGreen)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("[${formatMs(seg.startMs)}] $who", style = MaterialTheme.typography.labelMedium)
                }
                if (seg.lowConfidence) Text("建议核对", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(seg.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSegmentDialog(
    seg: SegmentEntity,
    case: CaseEntity?,
    segments: List<SegmentEntity>,
    onDismiss: () -> Unit,
    onSaveText: (String) -> Unit,
    onPickSpeaker: (String) -> Unit
) {
    var text by remember { mutableStateOf(seg.text) }
    var customMode by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    val roles = RoleLabels.rolesInCase(case, segments)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑这一段") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("这句话是谁说的？（只改这一段，不影响全篇）", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.forEach { role ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (role == "其他") customMode = true else onPickSpeaker(role)
                            },
                            label = { Text(role) }
                        )
                    }
                }
                if (customMode) {
                    Spacer(Modifier.height(10.dp))
                    Text("其他（自己填名字）：", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("角色名") },
                        placeholder = { Text("如：婆婆 / 闺蜜 / 前夫") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        TextButton(onClick = {
                            val name = customName.trim()
                            if (name.isNotBlank()) onPickSpeaker(name)
                        }) { Text("用这个名字") }
                        TextButton(onClick = { customMode = false }) { Text("取消") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSaveText(text) }) { Text("保存文字") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
