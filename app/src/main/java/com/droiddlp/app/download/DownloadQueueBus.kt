package com.droiddlp.app.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One queued / active / finished download. */
data class DownloadItem(
    val id: Long,
    val title: String,
    val state: DownloadState,
) {
    val isActive: Boolean
        get() = state is DownloadState.Queued || state is DownloadState.Running
}

/**
 * Process-wide queue of downloads, published by [DownloadService] to the UI.
 * Supports multiple concurrent downloads. CLAUDE.md §6 P1.
 */
object DownloadQueueBus {
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    /** Inserts a new item or replaces the existing one with the same id (order kept). */
    fun update(item: DownloadItem) {
        val current = _items.value
        _items.value =
            if (current.any { it.id == item.id }) {
                current.map { if (it.id == item.id) item else it }
            } else {
                current + item
            }
    }

    fun clearFinished() {
        _items.value = _items.value.filter { it.isActive }
    }
}
