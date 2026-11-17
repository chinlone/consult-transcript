package com.xiaodong.consulttranscript.ui.transcript

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaodong.consulttranscript.App
import com.xiaodong.consulttranscript.data.db.CaseEntity
import com.xiaodong.consulttranscript.data.db.SegmentEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository
    private val caseIdFlow = MutableStateFlow("")

    // ★ 关键修复：必须用 observeCase（Room 响应式 Flow），而不是一次性的 flow{emit(...)}。
    // 旧写法下 setSpeakerAMap 写库后这里不会重新发射，「A 是谁」弹窗判断条件一直成立、
    // 点任何按钮都没反应。改成响应式后，数据库一变 → case 立即刷新 → 弹窗自动消失。
    val case: StateFlow<CaseEntity?> = caseIdFlow.flatMapLatest { id ->
        if (id.isBlank()) flowOf<CaseEntity?>(null) else repo.observeCase(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val segments: StateFlow<List<SegmentEntity>> = caseIdFlow.flatMapLatest { id ->
        if (id.isBlank()) flowOf<List<SegmentEntity>>(emptyList()) else repo.observeSegments(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun bind(caseId: String) { caseIdFlow.value = caseId }

    fun setSpeakerAMap(role: String) = viewModelScope.launch {
        repo.setSpeakerAMap(caseIdFlow.value, role)
    }

    /** 保存每个聚类标签(A/B/C/D)对应的角色名，改一次全篇跟着变 */
    fun setSpeakerLabels(labels: Map<String, String>) = viewModelScope.launch {
        repo.setSpeakerLabels(caseIdFlow.value, labels)
    }

    /** 全局对调两个标签（两个人叫反了），不动单段手动改过的段 */
    fun swapLabels(from: String, to: String) = viewModelScope.launch {
        repo.relabelSegments(caseIdFlow.value, from, to)
        val c = repo.getCase(caseIdFlow.value) ?: return@launch
        val map = com.xiaodong.consulttranscript.core.diar.RoleLabels.parse(c.speakerLabels).toMutableMap()
        val fromRole = map[from] ?: "说话人$from"
        val toRole = map[to] ?: "说话人$to"
        map[from] = toRole; map[to] = fromRole
        repo.setSpeakerLabels(caseIdFlow.value, map)
    }

    fun changeSpeaker(seg: SegmentEntity, speaker: String) = viewModelScope.launch {
        repo.overrideSpeaker(seg, speaker)
    }

    fun editText(seg: SegmentEntity, text: String) = viewModelScope.launch {
        repo.editText(seg, text)
    }

    fun deleteKeptAudio() = viewModelScope.launch {
        repo.deleteKeptAudio(caseIdFlow.value)
    }
}
