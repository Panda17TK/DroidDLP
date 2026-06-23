package com.droiddlp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droiddlp.app.download.CompositeStreamExtractor
import com.droiddlp.app.download.DirectUrlStreamExtractor
import com.droiddlp.app.download.DownloadProgressBus
import com.droiddlp.app.download.DownloadService
import com.droiddlp.app.download.DownloadState
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
    val download: DownloadState? = null,
    val error: String? = null,
)

/**
 * Resolves a URL to its formats (in the ViewModel), lets the UI pick one, and
 * hands the chosen format to the foreground [DownloadService] (whose progress is
 * mirrored back via [DownloadProgressBus]). CLAUDE.md §6 P1.
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
            DownloadProgressBus.state.collect { download ->
                if (download != null) {
                    _state.value =
                        _state.value.copy(
                            download = download,
                            error = (download as? DownloadState.Failed)?.message,
                        )
                }
            }
        }
    }

    fun resolve(url: String) {
        val target = url.trim()
        if (target.isEmpty()) {
            _state.value = DownloadUiState(error = "URL is required")
            return
        }
        resolveJob?.cancel()
        DownloadProgressBus.reset()
        _state.value = DownloadUiState(resolving = true)
        resolveJob =
            viewModelScope.launch {
                _state.value =
                    try {
                        val info = extractor.extract(target)
                        if (info.formats.isEmpty()) {
                            DownloadUiState(info = info, error = "No downloadable formats found")
                        } else {
                            DownloadUiState(info = info)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DownloadUiState(error = e.message ?: e.javaClass.simpleName)
                    }
            }
    }

    fun download(format: StreamFormat) {
        val info = _state.value.info ?: return
        DownloadProgressBus.reset()
        _state.value = _state.value.copy(download = DownloadState.Queued, error = null)
        DownloadService.start(
            getApplication(),
            url = format.url,
            fileName = buildFileName(info.title, format.container),
            mime = format.mimeType,
        )
    }

    fun cancel() {
        DownloadService.cancel(getApplication())
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
