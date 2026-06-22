package com.droiddlp.app.potoken

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM unit tests for [WebPoTokenProvider].
 *
 * A real [android.webkit.WebView] cannot be instantiated under plain
 * `testDebugUnitTest` (it needs the Android runtime), so this scope asserts only
 * framework-free invariants: the type contract and the frozen seam constants.
 *
 * `quoteForJs` is intentionally NOT asserted here: it calls
 * `org.json.JSONObject.quote`, which is a throwing stub on the JVM unit-test
 * classpath ("not mocked"). Its behaviour is verified at the instrumented tier
 * once that harness exists (a later unit), to keep this suite device-free.
 */
class WebPoTokenProviderTest {
    @Test
    fun `is a PoTokenProvider`() {
        assertTrue(
            PoTokenProvider::class.java.isAssignableFrom(WebPoTokenProvider::class.java),
        )
    }

    @Test
    fun `bridge name and host url are the frozen seam values`() {
        assertEquals("DroidDlpBridge", WebPoTokenProvider.BRIDGE_NAME)
        assertEquals(
            "file:///android_asset/potoken/potoken.html",
            WebPoTokenProvider.HOST_URL,
        )
    }
}
