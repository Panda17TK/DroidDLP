package com.droiddlp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droiddlp.app.download.CompositeStreamExtractor
import com.droiddlp.app.download.DirectUrlStreamExtractor
import com.droiddlp.app.download.DownloadItem
import com.droiddlp.app.download.DownloadQueueBus
import com.droiddlp.app.download.DownloadService
import com.droiddlp.app.download.NewPipeStreamExtractor
import com.droiddlp.app.download.StreamExtractor
import com.droiddlp.app.download.StreamFormat
import com.droiddlp.app.download.StreamInfo
import com.droiddlp.app.potoken.PoTokenProviders
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** State for the download screen. */
data class DownloadUiState(
    val resolving: Boolean = false,
    val info: StreamInfo? = null,
    val error: String? = null,
    val queue: List<DownloadItem> = emptyList(),
)

/**
 * Resolves a URL to its formats (in the ViewModel), lets the UI pick one (and add
 * it to the concurrent download queue), and mirrors the [DownloadQueueBus] into UI
 * state. CLAUDE.md §6 P1.
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val extractor: StreamExtractor =
        CompositeStreamExtractor(
            listOf(
                NewPipeStreamExtractor(PoTokenProviders.default(application)),
                DirectUrlStreamExtractor(),
            ),
        )

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private var resolveJob: Job? = null

    init {
        viewModelScope.launch {
            DownloadQueueBus.items.collect { items -> _state.value = _state.value.copy(queue = items) }
        }
    }

    fun resolve(url: String) {
        val target = url.trim()
        if (target.isEmpty()) {
            _state.value = _state.value.copy(resolving = false, info = null, error = "URL is required")
            return
        }
        resolveJob?.cancel()
        _state.value = _state.value.copy(resolving = true, info = null, error = null)
        resolveJob =
            viewModelScope.launch {
                _state.value =
                    try {
                        val info = extractor.extract(target)
                        if (info.formats.isEmpty()) {
                            _state.value.copy(resolving = false, info = info, error = "No downloadable formats found")
                        } else {
                            _state.value.copy(resolving = false, info = info, error = null)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.value.copy(resolving = false, error = e.message ?: e.javaClass.simpleName)
                    }
            }
    }

    fun download(format: StreamFormat) {
        val info = _state.value.info ?: return
        DownloadService.start(
            getApplication(),
            url = format.url,
            fileName = buildFileName(info.title, format.container),
            mime = format.mimeType,
        )
    }

    fun cancel(id: Long) {
        DownloadService.cancel(getApplication(), id)
    }

    private fun buildFileName(
        title: String,
        container: String,
    ): String {
        val safe = title.replace(ILLEGAL_FILENAME_CHARS, "_").take(MAX_FILENAME_LENGTH).ifBlank { "download" }
        return if (safe.endsWith(".$container", ignoreCase = true)) safe else "$safe.$container"
    }

    private companion object {
        val ILLEGAL_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")
        const val MAX_FILENAME_LENGTH = 100
    }
}
