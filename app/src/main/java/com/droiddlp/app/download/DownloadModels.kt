package com.droiddlp.app.download

/** A pending download: a direct media [url] saved as [fileName] with [mimeType]. */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String,
)

/** Lifecycle of a single download. */
sealed interface DownloadState {
    data object Queued : DownloadState

    data class Running(
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : DownloadState {
        /** 0..100, or `null` when the total size is unknown. */
        val percent: Int?
            get() =
                totalBytes
                    ?.takeIf { it > 0 }
                    ?.let { ((downloadedBytes.coerceAtMost(it) * 100) / it).toInt() }
    }

    data class Completed(val uri: String) : DownloadState

    data class Failed(val message: String) : DownloadState
}
