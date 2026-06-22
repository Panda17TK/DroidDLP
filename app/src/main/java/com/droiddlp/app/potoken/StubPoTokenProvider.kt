package com.droiddlp.app.potoken

/**
 * Placeholder [PoTokenProvider] that always reports "no token available".
 *
 * Used at call sites that must be buildable/testable without standing up the
 * WebView-backed [WebPoTokenProvider] (e.g. previews, tests, or environments where
 * PoToken generation is intentionally disabled).
 */
class StubPoTokenProvider : PoTokenProvider {
    override suspend fun getPoToken(
        videoId: String,
        visitorData: String?,
    ): PoTokenResult? = null
}
