package com.droiddlp.app.potoken

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only E2E for the real WebView BotGuard solve (CLAUDE.md §6 P0-3 E2E).
 *
 * This actually hits live YouTube JNN endpoints and runs the BotGuard VM in a real
 * System WebView, so it can only run on a device/emulator with network and an
 * up-to-date WebView. It is NOT part of `assembleDebug` or `testDebugUnitTest`.
 *
 * Run on a connected device:
 *   ./gradlew connectedDebugAndroidTest
 *
 * A failure here most likely means a JNN 403 (headers/UA/key), a stale WebView, or
 * that the virtual youtube.com origin did not satisfy BotGuard — see the §6 P0-3
 * device-only notes.
 */
@RunWith(AndroidJUnit4::class)
class WebPoTokenProviderInstrumentedTest {
    @Test
    fun generatesPoTokenForAKnownVideo() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val provider = WebPoTokenProvider(context)

            val result = provider.getPoToken(videoId = "dQw4w9WgXcQ", visitorData = null)

            assertNotNull("PoToken solve returned null (check WebView / network / JNN)", result)
            assertTrue("player token must be non-empty", result!!.playerRequestPoToken.isNotEmpty())
            assertTrue("streaming token must be non-empty", result.streamingDataPoToken.isNotEmpty())
        }
}
