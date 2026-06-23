package com.droiddlp.app.potoken

import android.content.Context

/**
 * Builds the app's [PoTokenProvider]: the WebView BotGuard solver behind a TTL
 * cache + retry. The single place that selects the concrete provider, so call
 * sites depend only on [PoTokenProvider]. (CLAUDE.md §6 P0-4.)
 */
object PoTokenProviders {
    fun default(context: Context): PoTokenProvider = CachingPoTokenProvider(WebPoTokenProvider(context.applicationContext))
}
