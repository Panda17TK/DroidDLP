package com.droiddlp.app.potoken

import org.json.JSONArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM unit tests for [WebPoTokenProvider].
 *
 * A real [android.webkit.WebView] cannot be instantiated under plain
 * `testDebugUnitTest`, so this scope asserts framework-free invariants: the type
 * contract, the frozen seam constants, [PoTokenResult] field order, and the
 * [WebPoTokenProvider.quoteForJs] escaping used for Kotlin->JS injection. The
 * `org.json` calls rely on the real `org.json` test dependency shadowing the
 * throwing android.jar stub.
 */
class WebPoTokenProviderTest {
    @Test
    fun `is a PoTokenProvider`() {
        assertTrue(
            PoTokenProvider::class.java.isAssignableFrom(WebPoTokenProvider::class.java),
        )
    }

    @Test
    fun `bridge name and base url are the frozen seam values`() {
        assertEquals("DroidDlpBridge", WebPoTokenProvider.BRIDGE_NAME)
        assertEquals("https://www.youtube.com", WebPoTokenProvider.BASE_URL)
    }

    @Test
    fun `PoTokenResult field order is player-streaming-visitorData`() {
        val r = PoTokenResult(playerRequestPoToken = "P", streamingDataPoToken = "S", visitorData = "V")
        assertEquals("P", r.playerRequestPoToken)
        assertEquals("S", r.streamingDataPoToken)
        assertEquals("V", r.visitorData)
    }

    @Test
    fun `quoteForJs wraps in quotes and escapes a closing-paren injection`() {
        val out = WebPoTokenProvider.quoteForJs("\");evil(\"")
        assertTrue(out.startsWith("\"") && out.endsWith("\""))
        assertFalse(out.contains("\");evil(\"")) // raw dangerous sequence must be escaped
    }

    @Test
    fun `quoteForJs output round-trips through a JSON parser back to the original`() {
        // Behavioral, not byte-exact (robust across org.json versions).
        val original = "a\\b\nc\"d</script>"
        val quoted = WebPoTokenProvider.quoteForJs(original)
        val parsed = JSONArray("[$quoted]").getString(0)
        assertEquals(original, parsed)
    }

    @Test
    fun `injected solve source keeps an evil identifier as data, not live code`() {
        val evil = "\");DroidDlpBridge.onPoTokenError(\"x"
        val js =
            "window.droidDlpSolveChallenge(" +
                "${WebPoTokenProvider.quoteForJs("{}")}, ${WebPoTokenProvider.quoteForJs(evil)});"
        assertTrue(js.contains("droidDlpSolveChallenge("))
        assertFalse(js.contains("onPoTokenError(\"x")) // not present as live (unescaped) code
    }
}
