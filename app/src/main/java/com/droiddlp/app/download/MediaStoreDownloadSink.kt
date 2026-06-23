package com.droiddlp.app.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/**
 * [DownloadSink] writing into the system Downloads collection via MediaStore
 * (Scoped Storage). No storage permission is needed on API 29+. Files land in
 * `Downloads/[subDir]` and stay `IS_PENDING` until [SinkTarget.commit].
 * CLAUDE.md §6 P1.
 */
class MediaStoreDownloadSink(
    context: Context,
    private val subDir: String = "DroidDLP",
) : DownloadSink {
    private val appContext = context.applicationContext

    override suspend fun create(
        fileName: String,
        mimeType: String,
    ): SinkTarget =
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$subDir",
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("MediaStore insert failed")
            val output =
                resolver.openOutputStream(uri) ?: run {
                    resolver.delete(uri, null, null)
                    throw IOException("Could not open output stream for $uri")
                }
            MediaStoreTarget(appContext, uri, output)
        }

    private class MediaStoreTarget(
        private val context: Context,
        private val uri: Uri,
        override val stream: OutputStream,
    ) : SinkTarget {
        override suspend fun commit(): String =
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, values, null, null)
                uri.toString()
            }

        override suspend fun discard() {
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }
}
