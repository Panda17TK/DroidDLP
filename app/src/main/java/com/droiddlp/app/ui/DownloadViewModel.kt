package com.droiddlp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droiddlp.app.download.CompositeStreamExtractor
import com.droiddlp.app.download.DirectUrlStreamExtractor
import com.droiddlp.app.download.DownloadEngine
import com.droiddlp.app.download.DownloadRequest
import com.droiddlp.app.download.DownloadState
import com.droiddlp.app.download.HttpByteSource
import com.droiddlp.app.download.MediaStoreDownloadSink
import com.droiddlp.app.download.NewPipeStreamExtractor
import com.droiddlp.app.download.StreamExtractor
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
    val info: StreamInfo? = null,
    val download: DownloadState? = null,
    val resolving: Boolean = false,
    val error: String? = null,
)

/**
 * Resolves a URL via the [StreamExtractor] seam and streams the chosen format to
 * the [DownloadEngine], surfacing progress to the UI. Currently uses
 * [DirectUrlStreamExtractor] (direct media URLs); a YouTube backend drops in
 * behind the same seam. CLAUDE.md §6 P1.
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val extractor: StreamExtractor =
        CompositeStreamExtractor(
            listOf(
                NewPipeStreamExtractor(PoTokenProviders.default(application)),
                DirectUrlStreamExtractor(),
            ),
        )
    private val engine = DownloadEngine(HttpByteSource(), MediaStoreDownloadSink(application))

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(url: String) {
        val target = url.trim()
        when {
            target.isEmpty() -> {
                _state.value = DownloadUiState(error = "URL is required")
                return
            }

            !extractor.canHandle(target) -> {
                _state.value = DownloadUiState(error = "Only http/https URLs are supported")
                return
            }
        }

        job?.cancel()
        _state.value = DownloadUiState(resolving = true)
        job =
            viewModelScope.launch {
                val info =
                    try {
                        extractor.extract(target)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.value = DownloadUiState(error = e.message ?: e.javaClass.simpleName)
                        return@launch
                    }

                val format = info.formats.firstOrNull()
                if (format == null) {
                    _state.value = DownloadUiState(info = info, error = "No downloadable format found")
                    return@launch
                }

                val request = DownloadRequest(format.url, info.title, format.mimeType)
                engine.download(request).collect { downloadState ->
                    _state.value =
                        DownloadUiState(
                            info = info,
                            download = downloadState,
                            error = (downloadState as? DownloadState.Failed)?.message,
                        )
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = DownloadUiState()
    }
}
