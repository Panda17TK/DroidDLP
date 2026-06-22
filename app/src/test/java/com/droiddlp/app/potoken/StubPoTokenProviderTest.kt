package com.droiddlp.app.potoken

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull

class StubPoTokenProviderTest {
    @Test
    fun returns_null_until_real_provider_is_wired() =
        runBlocking {
            val provider: PoTokenProvider = StubPoTokenProvider()
            assertNull(provider.getPoToken("dQw4w9WgXcQ"))
        }
}
