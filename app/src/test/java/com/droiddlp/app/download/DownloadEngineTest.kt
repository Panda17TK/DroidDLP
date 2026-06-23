package com.droiddlp.app.download

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeByteSource(
    private val data: ByteArray,
    private val contentLength: Long? = data.size.toLong(),
    private val failOnOpen: Boolean = false,
) : ByteSource {
    override suspend fun open(url: String): SourceStream {
        if (failOnOpen) throw IOException("open boom")
        return SourceStream(ByteArrayInputStream(data), contentLength)
    }
}

private class FakeSink : DownloadSink {
    var committed = false
        private set
    var discarded = false
        private set
    val output = ByteArrayOutputStream()

    override suspend fun create(
        fileName: String,
        mimeType: String,
    ): SinkTarget =
        object : SinkTarget {
            override val stream: OutputStream = output

            override suspend fun commit(): String {
                committed = true
                return "mem://$fileName"
            }

            override suspend fun discard() {
                discarded = true
            }
        }
}

class DownloadEngineTest {
    private val request = DownloadRequest("http://x/file.bin", "file.bin", "application/octet-stream")

    @Test
    fun `downloads all bytes and completes with the sink uri`() =
        runBlocking {
            val data = ByteArray(200_000) { (it % 256).toByte() }
            val sink = FakeSink()
            val engine = DownloadEngine(FakeByteSource(data), sink, bufferSize = 16 * 1024)

            val states = engine.download(request).toList()

            assertIs<DownloadState.Queued>(states.first())
            val completed = states.last()
            assertIs<DownloadState.Completed>(completed)
            assertEquals("mem://file.bin", completed.uri)
            assertTrue(sink.committed)
            assertEquals(data.size, sink.output.size())
            assertEquals(data.toList(), sink.output.toByteArray().toList())
        }

    @Test
    fun `running progress reaches 100 percent when total is known`() =
        runBlocking {
            val data = ByteArray(100_000)
            val engine = DownloadEngine(FakeByteSource(data), FakeSink(), bufferSize = 8 * 1024)

            val running =
                engine.download(request).toList().filterIsInstance<DownloadState.Running>()

            assertTrue(running.isNotEmpty())
            assertEquals(data.size.toLong(), running.last().downloadedBytes)
            assertEquals(100, running.last().percent)
        }

    @Test
    fun `percent is null when total size is unknown`() =
        runBlocking {
            val data = ByteArray(50_000)
            val engine = DownloadEngine(FakeByteSource(data, contentLength = null), FakeSink())

            val running =
                engine.download(request).toList().filterIsInstance<DownloadState.Running>()

            assertTrue(running.isNotEmpty())
            assertTrue(running.all { it.percent == null })
        }

    @Test
    fun `open failure emits Failed`() =
        runBlocking {
            val engine = DownloadEngine(FakeByteSource(ByteArray(0), failOnOpen = true), FakeSink())

            val states = engine.download(request).toList()

            // assertTrue (not assertIs) so the test method returns Unit/void for JUnit4.
            assertTrue(states.last() is DownloadState.Failed)
        }
}
