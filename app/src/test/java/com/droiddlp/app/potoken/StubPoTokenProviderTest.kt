package com.droiddlp.app.potoken

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull

class StubPoTokenProviderTest {
    @Test
    fun returns_null_for_any_input() =
        runBlocking {
            val provider: PoTokenProvider = StubPoTokenProvider()
            assertNull(provider.getPoToken("dQw4w9WgXcQ", visitorData = null))
            assertNull(provider.getPoToken("dQw4w9WgXcQ", visitorData = "VISITOR_DATA"))
        }
}
