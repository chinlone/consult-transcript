package com.xiaodong.consulttranscript.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaodong.consulttranscript.App
import com.xiaodong.consulttranscript.data.db.CaseEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository
    val cases: StateFlow<List<CaseEntity>> = repo.observeCases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
