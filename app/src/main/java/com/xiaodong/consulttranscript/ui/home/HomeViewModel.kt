package com.xiaodong.consulttranscript.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaodong.consulttranscript.App
import com.xiaodong.consulttranscript.core.audio.RecordingState
import com.xiaodong.consulttranscript.data.db.CaseEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository
    val cases: StateFlow<List<CaseEntity>> = repo.observeCases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 删除一条咨询记录：连带清音频、临时文件、以及可能残留的录音状态 */
    fun deleteCase(c: CaseEntity) = viewModelScope.launch {
        if (c.status == "RECORDING" && RecordingState.activeCaseId(getApplication()) == c.id) {
            RecordingState.clear(getApplication())
        }
        repo.deleteCase(c.id)
        repo.vault.deleteKept(c.id)
        repo.vault.discard(c.id)
    }
}
