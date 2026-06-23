package com.droiddlp.app.download

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectUrlStreamExtractorTest {
    private val extractor = DirectUrlStreamExtractor()

    @Test
    fun `canHandle accepts http and https only`() {
        assertTrue(extractor.canHandle("https://x.com/a.mp4"))
        assertTrue(extractor.canHandle("HTTP://x.com/a.mp4"))
        assertFalse(extractor.canHandle("ftp://x.com/a.mp4"))
        assertFalse(extractor.canHandle("not a url"))
    }

    @Test
    fun `fileNameFrom strips query and fragment`() {
        assertEquals("video.mp4", DirectUrlStreamExtractor.fileNameFrom("https://x.com/a/b/video.mp4?q=1#t=2"))
    }

    @Test
    fun `fileNameFrom adds bin extension when none present`() {
        assertEquals("clip.bin", DirectUrlStreamExtractor.fileNameFrom("https://x.com/path/clip"))
    }

    @Test
    fun `mediaTypeFrom maps known extensions`() {
        assertEquals("mp4" to "video/mp4", DirectUrlStreamExtractor.mediaTypeFrom("v.mp4"))
        assertEquals("m4a" to "audio/mp4", DirectUrlStreamExtractor.mediaTypeFrom("song.m4a"))
        assertEquals("bin" to "application/octet-stream", DirectUrlStreamExtractor.mediaTypeFrom("noext"))
    }

    @Test
    fun `extract builds one format with audio kind for audio mime`() =
        runBlocking {
            val info = extractor.extract("https://x.com/song.m4a")
            assertEquals(1, info.formats.size)
            val format = info.formats.first()
            assertEquals("https://x.com/song.m4a", format.url)
            assertEquals("audio/mp4", format.mimeType)
            assertEquals(StreamKind.AUDIO, format.kind)
        }
}
