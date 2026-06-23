package com.droiddlp.app.download

/** Whether a [StreamFormat] carries video, audio, or video without audio. */
enum class StreamKind { VIDEO, VIDEO_ONLY, AUDIO }

/** One downloadable representation of a stream (a direct media URL + metadata). */
data class StreamFormat(
    val id: String,
    val url: String,
    val label: String,
    val container: String,
    val mimeType: String,
    val kind: StreamKind,
    val sizeBytes: Long? = null,
)

/** Resolved info for a source URL: a title and the formats available to download. */
data class StreamInfo(
    val sourceUrl: String,
    val title: String,
    val formats: List<StreamFormat>,
)
