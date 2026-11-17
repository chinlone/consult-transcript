package com.xiaodong.consulttranscript.ui.importaudio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.xiaodong.consulttranscript.App
import com.xiaodong.consulttranscript.core.audio.AudioImporter
import com.xiaodong.consulttranscript.data.db.CaseEntity
import com.xiaodong.consulttranscript.work.TranscriptionWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 导入页状态机：idle → decoding → enqueued。
 * 解码在主线程外流式进行，进度回传界面；完成后和普通录音一样交给 WorkManager 转写。
 */
class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository

    sealed interface State {
        data object Idle : State
        data class Decoding(val percent: Int, val message: String) : State
        data class Done(val caseId: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /**
     * @param keepAudio 是否保留原始录音。导入场景默认不保留：源文件本来就在用户手机里，
     *                  成稿后删掉 App 内的临时 PCM 即可，不额外占空间。
     */
    fun startImport(
        uri: Uri,
        alias: String,
        speakerCount: Int,
        keepAudio: Boolean,
        onDone: (String) -> Unit
    ) = viewModelScope.launch {
        _state.value = State.Decoding(0, "准备解码…")
        try {
            val c: CaseEntity = repo.createCase(
                alias = alias.trim(),
                keepAudio = keepAudio,
                speakerCount = speakerCount
            )
            val target = repo.vault.newTempFile(c.id)
            val importer = AudioImporter(getApplication())
            val durationMs = importer.import(uri, target) { p ->
                _state.value = State.Decoding(p.percent, p.message)
            }
            repo.updateCase(c.copy(durationMs = durationMs))

            val req = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(workDataOf(TranscriptionWorker.KEY_CASE_ID to c.id))
                .addTag("case_${c.id}")
                .build()
            WorkManager.getInstance(getApplication()).enqueue(req)

            _state.value = State.Done(c.id)
            onDone(c.id)
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "导入失败，请换一个文件重试")
        }
    }
}
