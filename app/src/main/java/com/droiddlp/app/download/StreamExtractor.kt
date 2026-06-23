package com.droiddlp.app.download

/**
 * Resolves a page/share URL into downloadable [StreamInfo]. Implementations are
 * pluggable behind this seam (a direct-URL passthrough today; a YouTube extractor
 * later) so the rest of the download stack is backend-agnostic. CLAUDE.md §6 P1.
 */
interface StreamExtractor {
    /** Whether this extractor recognizes [url]. */
    fun canHandle(url: String): Boolean

    /** Resolves [url] to its title and downloadable formats. */
    suspend fun extract(url: String): StreamInfo
}
