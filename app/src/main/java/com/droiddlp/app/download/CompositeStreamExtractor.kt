package com.droiddlp.app.download

/** Tries each [StreamExtractor] in order; the first that [canHandle]s the URL wins. */
class CompositeStreamExtractor(
    private val extractors: List<StreamExtractor>,
) : StreamExtractor {
    override fun canHandle(url: String): Boolean = extractors.any { it.canHandle(url) }

    override suspend fun extract(url: String): StreamInfo {
        val extractor =
            extractors.firstOrNull { it.canHandle(url) }
                ?: throw IllegalArgumentException("No extractor handles: $url")
        return extractor.extract(url)
    }
}
