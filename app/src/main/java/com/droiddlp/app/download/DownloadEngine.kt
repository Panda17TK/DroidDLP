package com.droiddlp.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

/** A byte stream plus its total length (when the server advertises one). */
class SourceStream(
    val stream: InputStream,
    val contentLength: Long?,
)

/** Opens a readable byte stream for a URL. Abstracted so the engine is testable. */
interface ByteSource {
    suspend fun open(url: String): SourceStream
}

/** A writable destination that can be committed (made visible) or discarded. */
interface SinkTarget {
    val stream: OutputStream

    /** Finalizes the file and returns a stable URI/path string. */
    suspend fun commit(): String

    /** Removes the partially-written file. */
    suspend fun discard()
}

/** Creates destinations for downloads (e.g. MediaStore Downloads). */
interface DownloadSink {
    suspend fun create(
        fileName: String,
        mimeType: String,
    ): SinkTarget
}

/**
 * Streams a [DownloadRequest] from a [ByteSource] to a [DownloadSink], emitting
 * progress. Cancellation or failure discards the partial file. The source and
 * sink are injected, so the copy loop is unit-testable without HTTP or
 * MediaStore. CLAUDE.md §6 P1.
 */
class DownloadEngine(
    private val source: ByteSource,
    private val sink: DownloadSink,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) {
    fun download(request: DownloadRequest): Flow<DownloadState> =
        flow {
            emit(DownloadState.Queued)

            var target: SinkTarget? = null
            val terminal: DownloadState =
                try {
                    val src = source.open(request.url)
                    val sinkTarget = sink.create(request.fileName, request.mimeType)
                    target = sinkTarget

                    var downloaded = 0L
                    val buffer = ByteArray(bufferSize)
                    src.stream.use { input ->
                        sinkTarget.stream.use { output ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                // emit() is a cancellation checkpoint: cancelling
                                // here unwinds into the catch and discards the partial.
                                emit(DownloadState.Running(downloaded, src.contentLength))
                            }
                        }
                    }
                    DownloadState.Completed(sinkTarget.commit())
                } catch (e: CancellationException) {
                    target?.discard()
                    throw e
                } catch (e: Exception) {
                    target?.discard()
                    DownloadState.Failed(e.message ?: e.javaClass.simpleName)
                }

            emit(terminal)
        }.flowOn(Dispatchers.IO)

    companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
