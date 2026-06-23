package com.droiddlp.app.download

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * NewPipeExtractor [Downloader] backed by [HttpURLConnection] (no OkHttp). The
 * GPLv3 NewPipeExtractor calls [execute] for every HTTP request it makes during
 * extraction. CLAUDE.md §6 P1-2.
 */
class NewPipeDownloader(
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : Downloader() {
    override fun execute(request: Request): Response {
        val connection =
            (URL(request.url()).openConnection() as HttpURLConnection).apply {
                requestMethod = request.httpMethod()
                instanceFollowRedirects = true
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                for ((name, values) in request.headers()) {
                    for (value in values) {
                        addRequestProperty(name, value)
                    }
                }
                request.dataToSend()?.let { body ->
                    doOutput = true
                    outputStream.use { it.write(body) }
                }
            }
        return try {
            val code = connection.responseCode
            val message = connection.responseMessage ?: ""
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            Response(code, message, connection.headerFields, body, connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000
    }
}
