package com.xiaodong.consulttranscript.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xiaodong.consulttranscript.App

class RecordViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as App).repository
}
