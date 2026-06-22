package com.droiddlp.app.potoken

/**
 * Placeholder [PoTokenProvider] used until the `bgutils-js` WebView wiring lands
 * (§6 P0-2..P0-4).
 *
 * It always reports "no token available" so call sites can be built and exercised
 * end-to-end without yet depending on the JS runtime. Replace with the WebView-backed
 * generator once available.
 */
class StubPoTokenProvider : PoTokenProvider {
    override suspend fun getPoToken(identifier: String): PoTokenResult? = null
}
