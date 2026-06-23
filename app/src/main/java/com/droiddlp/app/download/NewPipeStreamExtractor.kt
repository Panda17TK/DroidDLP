package com.droiddlp.app.download

import com.droiddlp.app.potoken.PoTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider as NewPipePoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult as NewPipePoTokenResult

/**
 * [StreamExtractor] backed by the GPLv3 NewPipeExtractor for YouTube URLs, with
 * DroidDLP's [PoTokenProvider] wired into NewPipe's web-client PoToken SPI.
 *
 * Note: resolving real streams needs network and a valid PoToken — and NewPipe's
 * web PoToken requires a `visitorData` that our provider must supply, so this is
 * device-verified and refined further (see CLAUDE.md §6 P1-2). The code here is
 * the compiling integration seam.
 */
class NewPipeStreamExtractor(
    private val poTokenProvider: PoTokenProvider,
    private val visitorDataProvider: VisitorDataProvider = YoutubeVisitorDataProvider(),
) : StreamExtractor {
    override fun canHandle(url: String): Boolean =
        runCatching {
            NewPipeBootstrap.ensureInitialized(poTokenProvider, visitorDataProvider)
            NewPipe.getServiceByUrl(url).serviceId == ServiceList.YouTube.serviceId
        }.getOrDefault(false)

    override suspend fun extract(url: String): StreamInfo =
        withContext(Dispatchers.IO) {
            NewPipeBootstrap.ensureInitialized(poTokenProvider, visitorDataProvider)
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()
            val title = extractor.name.ifBlank { "video" }
            val formats =
                buildList {
                    extractor.videoStreams.orEmpty()
                        .filter { it.isUrl }
                        .forEach { add(videoFormat(it, StreamKind.VIDEO)) }
                    extractor.audioStreams.orEmpty()
                        .filter { it.isUrl }
                        .forEach { add(audioFormat(it)) }
                    extractor.videoOnlyStreams.orEmpty()
                        .filter { it.isUrl }
                        .forEach { add(videoFormat(it, StreamKind.VIDEO_ONLY)) }
                }
            StreamInfo(sourceUrl = url, title = title, formats = formats)
        }

    private fun videoFormat(
        stream: VideoStream,
        kind: StreamKind,
    ): StreamFormat {
        val format = stream.format
        val suffix = format?.suffix ?: "mp4"
        val mime = format?.mimeType ?: "video/mp4"
        val resolution = stream.resolution.ifBlank { "video" }
        val onlyLabel = if (kind == StreamKind.VIDEO_ONLY) " (video only)" else ""
        return StreamFormat(
            id = "v-$resolution-$suffix-${kind.name}",
            url = stream.content,
            label = "$resolution $suffix$onlyLabel",
            container = suffix,
            mimeType = mime,
            kind = kind,
        )
    }

    private fun audioFormat(stream: AudioStream): StreamFormat {
        val format = stream.format
        val suffix = format?.suffix ?: "m4a"
        val mime = format?.mimeType ?: "audio/mp4"
        val bitrate = stream.averageBitrate
        val bitrateLabel = if (bitrate > 0) "${bitrate}kbps " else ""
        return StreamFormat(
            id = "a-$bitrate-$suffix",
            url = stream.content,
            label = "audio $bitrateLabel$suffix",
            container = suffix,
            mimeType = mime,
            kind = StreamKind.AUDIO,
        )
    }
}

/** One-time NewPipe init + PoToken bridge wiring (thread-safe, idempotent). */
internal object NewPipeBootstrap {
    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureInitialized(
        poTokenProvider: PoTokenProvider,
        visitorDataProvider: VisitorDataProvider,
    ) {
        if (initialized) return
        NewPipe.init(NewPipeDownloader())
        YoutubeStreamExtractor.setPoTokenProvider(
            DroidPoTokenBridge(poTokenProvider, visitorDataProvider),
        )
        initialized = true
    }
}

/** Adapts DroidDLP's [PoTokenProvider] to NewPipe's web-client PoToken SPI. */
private class DroidPoTokenBridge(
    private val provider: PoTokenProvider,
    private val visitorDataProvider: VisitorDataProvider,
) : NewPipePoTokenProvider {
    @Volatile
    private var cachedVisitorData: String? = null

    override fun getWebClientPoToken(videoId: String): NewPipePoTokenResult? =
        runBlocking {
            // Fetch (and cache) a fresh visitorData, then mint tokens bound to it:
            // player <- videoId, streaming <- visitorData.
            val visitorData =
                cachedVisitorData
                    ?: visitorDataProvider.fetch()?.also { cachedVisitorData = it }
                    ?: return@runBlocking null
            val result = provider.getPoToken(videoId, visitorData) ?: return@runBlocking null
            NewPipePoTokenResult(
                visitorData,
                result.playerRequestPoToken,
                result.streamingDataPoToken,
            )
        }

    override fun getWebEmbedClientPoToken(videoId: String): NewPipePoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String): NewPipePoTokenResult? = null

    override fun getIosClientPoToken(videoId: String): NewPipePoTokenResult? = null
}
