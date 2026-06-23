package com.droiddlp.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a fresh `visitorData` by reading it out of the YouTube homepage's
 * embedded `ytcfg` JSON in a single GET. Used to bind web-client PoTokens.
 *
 * Network-dependent and YouTube-internal: device-verified. CLAUDE.md §6 P1-2.
 */
class YoutubeVisitorDataProvider(
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : VisitorDataProvider {
    override suspend fun fetch(): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection =
                    (URL(HOME_URL).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                        connectTimeout = timeoutMillis
                        readTimeout = timeoutMillis
                        setRequestProperty("User-Agent", WEB_USER_AGENT)
                        setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                    }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    VISITOR_DATA_REGEX.find(body)?.groupValues?.getOrNull(1)?.let(::unescape)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    private fun unescape(raw: String): String =
        raw
            .replace("\\u003d", "=")
            .replace("\\u0026", "&")
            .replace("\\/", "/")

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000
        private const val HOME_URL = "https://www.youtube.com/"
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val VISITOR_DATA_REGEX = Regex("\"visitorData\"\\s*:\\s*\"([^\"]+)\"")
    }
}
