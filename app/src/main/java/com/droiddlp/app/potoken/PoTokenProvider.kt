package com.droiddlp.app.potoken

/**
 * Result of a PoToken (Proof-of-Origin Token) request.
 *
 * YouTube binds a PoToken to the player request and/or the streaming-data request, both
 * scoped to a visitor/session identity (`visitorData`). `bgutils-js` produces these once
 * the BotGuard VM has been solved — see CLAUDE.md §7.
 */
data class PoTokenResult(
    /** Token attached to the `/player` request (bound to the videoId). */
    val playerRequestPoToken: String,
    /** Token attached to streaming-data (download) requests (bound to visitorData). */
    val streamingDataPoToken: String,
    /** Visitor/session identity the streaming token was generated for, if known. */
    val visitorData: String? = null,
)

/**
 * Generates YouTube PoTokens on-device.
 *
 * The real implementation runs `bgutils-js` inside a hidden [android.webkit.WebView]
 * (§6 P0-3). Call sites depend only on this interface so the JS/WebView details stay
 * isolated behind it (boundary-abstraction rule, CLAUDE.md §5).
 */
interface PoTokenProvider {
    /**
     * @param videoId the video the player token is bound to.
     * @param visitorData session/visitor identity the streaming token is bound to; when
     *   `null` the streaming token falls back to a videoId binding.
     * @return the generated tokens, or `null` when PoToken generation is unavailable.
     */
    suspend fun getPoToken(
        videoId: String,
        visitorData: String?,
    ): PoTokenResult?
}
