package com.droiddlp.app.potoken

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [PoTokenProvider] decorator adding a TTL cache and a bounded retry around a
 * [delegate] (typically [WebPoTokenProvider]). CLAUDE.md §6 P0-5.
 *
 * - A successful result is cached per `(videoId, visitorData)` for [ttlMillis].
 * - A `null` (failed) attempt is retried up to [maxAttempts] times within a
 *   single call; failures are never cached.
 *
 * Minting a PoToken is expensive (a WebView solve + two network round-trips), so
 * repeat requests for the same video/visitor within the TTL reuse the cached
 * token instead of re-solving.
 *
 * @param ttlMillis how long a successful result stays fresh.
 * @param maxAttempts total delegate attempts per call (>= 1).
 * @param nowMillis injectable clock (defaults to wall-clock) for testability.
 */
class CachingPoTokenProvider(
    private val delegate: PoTokenProvider,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : PoTokenProvider {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    }

    private data class Key(val videoId: String, val visitorData: String?)

    private class Entry(val result: PoTokenResult, val expiresAt: Long)

    private val mutex = Mutex()
    private val cache = HashMap<Key, Entry>()

    override suspend fun getPoToken(
        videoId: String,
        visitorData: String?,
    ): PoTokenResult? {
        val key = Key(videoId, visitorData)

        cached(key)?.let { return it }

        var attempt = 0
        var result: PoTokenResult? = null
        while (result == null && attempt < maxAttempts) {
            result = delegate.getPoToken(videoId, visitorData)
            attempt++
        }

        result?.let { store(key, it) }
        return result
    }

    private suspend fun cached(key: Key): PoTokenResult? =
        mutex.withLock {
            val entry = cache[key] ?: return@withLock null
            if (entry.expiresAt > nowMillis()) {
                entry.result
            } else {
                cache.remove(key)
                null
            }
        }

    private suspend fun store(
        key: Key,
        result: PoTokenResult,
    ) {
        mutex.withLock { cache[key] = Entry(result, nowMillis() + ttlMillis) }
    }

    companion object {
        /** PoTokens are session-scoped; 6 hours is a conservative reuse window. */
        const val DEFAULT_TTL_MILLIS = 6 * 60 * 60 * 1000L

        /** One retry by default (the WebView solve is occasionally flaky). */
        const val DEFAULT_MAX_ATTEMPTS = 2
    }
}
