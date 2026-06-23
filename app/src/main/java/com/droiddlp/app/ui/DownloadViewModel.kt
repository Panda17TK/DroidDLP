package com.droiddlp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droiddlp.app.download.DownloadProgressBus
import com.droiddlp.app.download.DownloadService
import com.droiddlp.app.download.DownloadState
import com.droiddlp.app.download.StreamInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** State for the download screen. */
data class DownloadUiState(
    val info: StreamInfo? = null,
    val download: DownloadState? = null,
    val resolving: Boolean = false,
    val error: String? = null,
)

/**
 * Starts/cancels the foreground [DownloadService] and mirrors its progress
 * (published via [DownloadProgressBus]) into UI state, so a download keeps running
 * even if this screen is closed. CLAUDE.md §6 P1-4.
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(DownloadProgressBus.state, DownloadProgressBus.title) { download, title ->
                download to title
            }.collect { (download, title) ->
                if (download != null || title != null) {
                    _state.value =
                        DownloadUiState(
                            info = title?.let { StreamInfo(sourceUrl = "", title = it, formats = emptyList()) },
                            download = download,
                            resolving = download is DownloadState.Queued && title == null,
                            error = (download as? DownloadState.Failed)?.message,
                        )
                }
            }
        }
    }

    fun start(url: String) {
        val target = url.trim()
        if (target.isEmpty()) {
            _state.value = DownloadUiState(error = "URL is required")
            return
        }
        DownloadProgressBus.reset()
        _state.value = DownloadUiState(resolving = true)
        DownloadService.start(getApplication(), target)
    }

    fun cancel() {
        DownloadService.cancel(getApplication())
    }
}
