package com.droiddlp.app.potoken

/**
 * Result of a PoToken (Proof-of-Origin Token) request.
 *
 * YouTube binds a PoToken to the player request and/or the streaming-data request, both
 * scoped to a visitor/session identity (`visitorData`). `bgutils-js` produces these once
 * the BotGuard VM has been solved — see CLAUDE.md §7.
 */
data class PoTokenResult(
    /** Token attached to the `/player` request. */
    val playerRequestPoToken: String,
    /** Token attached to streaming-data (download) requests. */
    val streamingDataPoToken: String,
    /** Visitor/session identity the tokens were generated for, if known. */
    val visitorData: String? = null,
)

/**
 * Generates YouTube PoTokens on-device.
 *
 * The real implementation (§6 P0-2..P0-4) runs `bgutils-js` inside a hidden [android.webkit.WebView].
 * Call sites depend only on this interface so the JS/WebView details stay isolated behind it
 * (boundary-abstraction rule, CLAUDE.md §5).
 */
interface PoTokenProvider {
    /**
     * @param identifier content binding for the token — typically a `videoId` or `visitorData`.
     * @return the generated tokens, or `null` when PoToken generation is unavailable.
     */
    suspend fun getPoToken(identifier: String): PoTokenResult?
}
