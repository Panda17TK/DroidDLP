package com.droiddlp.app.download

/**
 * Obtains a fresh YouTube `visitorData`. NewPipe's web-client PoToken must be
 * bound to a visitorData, so the PoToken bridge fetches one here before minting.
 * CLAUDE.md §6 P1-2.
 */
interface VisitorDataProvider {
    /** Returns a fresh visitorData, or `null` on failure. */
    suspend fun fetch(): String?
}
