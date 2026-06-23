package com.droiddlp.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * [ByteSource] backed by [HttpURLConnection]. Follows redirects (media CDNs
 * commonly 302), reports the advertised content length, and closes the
 * connection when the returned stream is closed.
 */
class HttpByteSource(
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : ByteSource {
    override suspend fun open(url: String): SourceStream =
        withContext(Dispatchers.IO) {
            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = connectTimeoutMillis
                    readTimeout = readTimeoutMillis
                }
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                throw IOException("HTTP $code while opening download")
            }
            val length = connection.contentLengthLong.takeIf { it >= 0 }
            SourceStream(ConnectionInputStream(connection), length)
        }

    /** Disconnects the connection once the body stream is closed. */
    private class ConnectionInputStream(
        private val connection: HttpURLConnection,
    ) : FilterInputStream(connection.inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000
    }
}
