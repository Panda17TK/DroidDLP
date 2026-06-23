package com.droiddlp.app.download

/**
 * Passthrough [StreamExtractor] for direct media URLs: the input URL *is* the
 * download. Derives a filename and media type from the URL so the download stack
 * works end-to-end today, while the YouTube extractor is wired behind the same
 * seam later. CLAUDE.md §6 P1-2.
 */
class DirectUrlStreamExtractor : StreamExtractor {
    override fun canHandle(url: String): Boolean {
        val u = url.trim().lowercase()
        return u.startsWith("http://") || u.startsWith("https://")
    }

    override suspend fun extract(url: String): StreamInfo {
        val trimmed = url.trim()
        val fileName = fileNameFrom(trimmed)
        val (container, mimeType) = mediaTypeFrom(fileName)
        val kind = if (mimeType.startsWith("audio/")) StreamKind.AUDIO else StreamKind.VIDEO
        return StreamInfo(
            sourceUrl = trimmed,
            title = fileName,
            formats =
                listOf(
                    StreamFormat(
                        id = "direct",
                        url = trimmed,
                        label = "Direct ($container)",
                        container = container,
                        mimeType = mimeType,
                        kind = kind,
                    ),
                ),
        )
    }

    companion object {
        /** Best-effort filename from a URL's path; always has an extension. */
        fun fileNameFrom(url: String): String {
            val path = url.substringBefore('?').substringBefore('#')
            val last = path.trimEnd('/').substringAfterLast('/')
            val name = last.ifBlank { "download" }
            return if (name.contains('.')) name else "$name.bin"
        }

        /** Maps a filename extension to a (container, mimeType) pair. */
        fun mediaTypeFrom(fileName: String): Pair<String, String> =
            when (fileName.substringAfterLast('.', "").lowercase()) {
                "mp4", "m4v" -> "mp4" to "video/mp4"
                "webm" -> "webm" to "video/webm"
                "mkv" -> "mkv" to "video/x-matroska"
                "m4a" -> "m4a" to "audio/mp4"
                "mp3" -> "mp3" to "audio/mpeg"
                "opus" -> "opus" to "audio/opus"
                "ogg" -> "ogg" to "audio/ogg"
                "jpg", "jpeg" -> "jpg" to "image/jpeg"
                "png" -> "png" to "image/png"
                "webp" -> "webp" to "image/webp"
                else -> "bin" to "application/octet-stream"
            }
    }
}
