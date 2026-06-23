package com.droiddlp.app.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bus carrying the current download's state and title from the
 * foreground [DownloadService] to the UI. A single active download at a time
 * (KISS); a queue is a later refinement. CLAUDE.md §6 P1-4.
 */
object DownloadProgressBus {
    private val _state = MutableStateFlow<DownloadState?>(null)
    val state: StateFlow<DownloadState?> = _state.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    fun publish(state: DownloadState?) {
        _state.value = state
    }

    fun setTitle(title: String?) {
        _title.value = title
    }

    fun reset() {
        _state.value = null
        _title.value = null
    }
}
