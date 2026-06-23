package com.droiddlp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droiddlp.app.potoken.PoTokenProvider
import com.droiddlp.app.potoken.PoTokenProviders
import com.droiddlp.app.potoken.PoTokenResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** UI state for the PoToken verification screen. */
sealed interface PoTokenUiState {
    data object Idle : PoTokenUiState

    data object Loading : PoTokenUiState

    data class Success(val result: PoTokenResult) : PoTokenUiState

    data class Error(val message: String) : PoTokenUiState
}

/**
 * Drives an on-device PoToken generation attempt — the manual E2E harness for the
 * WebView BotGuard solver (CLAUDE.md §6 P0-3 E2E / P0-4 consumption seam).
 *
 * Treats the provider's output as untrusted: a `null` result is surfaced as an
 * error rather than assumed usable, and downstream consumers must validate it
 * before sending it to YouTube.
 */
class PoTokenViewModel(application: Application) : AndroidViewModel(application) {
    private val provider: PoTokenProvider = PoTokenProviders.default(application)

    private val _state = MutableStateFlow<PoTokenUiState>(PoTokenUiState.Idle)
    val state: StateFlow<PoTokenUiState> = _state.asStateFlow()

    fun generate(
        videoId: String,
        visitorData: String,
    ) {
        val video = videoId.trim()
        if (video.isEmpty()) {
            _state.value = PoTokenUiState.Error("videoId is required")
            return
        }
        val visitor = visitorData.trim().ifEmpty { null }
        _state.value = PoTokenUiState.Loading
        viewModelScope.launch {
            _state.value =
                try {
                    val result = provider.getPoToken(video, visitor)
                    if (result == null) {
                        PoTokenUiState.Error("PoToken generation returned null")
                    } else {
                        PoTokenUiState.Success(result)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PoTokenUiState.Error(e.message ?: e.javaClass.simpleName)
                }
        }
    }
}
