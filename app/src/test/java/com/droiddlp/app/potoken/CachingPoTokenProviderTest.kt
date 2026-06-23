package com.droiddlp.app.potoken

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Counting fake whose response is decided per call index. */
private class FakePoTokenProvider(
    private val responder: (videoId: String, visitorData: String?, callIndex: Int) -> PoTokenResult?,
) : PoTokenProvider {
    var calls = 0
        private set

    override suspend fun getPoToken(
        videoId: String,
        visitorData: String?,
    ): PoTokenResult? {
        val result = responder(videoId, visitorData, calls)
        calls++
        return result
    }
}

class CachingPoTokenProviderTest {
    private fun result(tag: String) = PoTokenResult("p-$tag", "s-$tag", "v-$tag")

    @Test
    fun `caches a success within TTL and does not call the delegate again`() =
        runBlocking {
            val fake = FakePoTokenProvider { _, _, _ -> result("a") }
            var now = 1_000L
            val caching = CachingPoTokenProvider(fake, ttlMillis = 100, nowMillis = { now })

            val first = caching.getPoToken("vid", null)
            now = 1_050L // still within TTL
            val second = caching.getPoToken("vid", null)

            assertEquals(result("a"), first)
            assertSame(first, second) // served from cache, same instance
            assertEquals(1, fake.calls)
        }

    @Test
    fun `refetches after the TTL expires`() =
        runBlocking {
            val fake = FakePoTokenProvider { _, _, i -> result(i.toString()) }
            var now = 0L
            val caching = CachingPoTokenProvider(fake, ttlMillis = 100, nowMillis = { now })

            caching.getPoToken("vid", null)
            now = 101L // past TTL
            caching.getPoToken("vid", null)

            assertEquals(2, fake.calls)
        }

    @Test
    fun `retries on null and caches the eventual success`() =
        runBlocking {
            val fake = FakePoTokenProvider { _, _, i -> if (i < 2) null else result("ok") }
            val caching = CachingPoTokenProvider(fake, maxAttempts = 3, nowMillis = { 0L })

            val r = caching.getPoToken("vid", null)

            assertEquals(result("ok"), r)
            assertEquals(3, fake.calls) // null, null, success
        }

    @Test
    fun `gives up after maxAttempts and does not cache failure`() =
        runBlocking {
            val fake = FakePoTokenProvider { _, _, _ -> null }
            val caching = CachingPoTokenProvider(fake, maxAttempts = 2, nowMillis = { 0L })

            val first = caching.getPoToken("vid", null)
            val second = caching.getPoToken("vid", null)

            assertNull(first)
            assertNull(second)
            assertEquals(4, fake.calls) // 2 attempts per call; failure not cached
        }

    @Test
    fun `caches distinct keys separately`() =
        runBlocking {
            val fake =
                FakePoTokenProvider { vid, visitor, _ -> PoTokenResult("p-$vid", "s-$visitor", visitor) }
            val caching = CachingPoTokenProvider(fake, nowMillis = { 0L })

            val a = caching.getPoToken("v1", "x")
            val b = caching.getPoToken("v2", "x")
            val aAgain = caching.getPoToken("v1", "x")

            assertEquals("p-v1", a?.playerRequestPoToken)
            assertEquals("p-v2", b?.playerRequestPoToken)
            assertSame(a, aAgain) // v1 served from cache
            assertEquals(2, fake.calls) // only v1 and v2 hit the delegate
        }
}
