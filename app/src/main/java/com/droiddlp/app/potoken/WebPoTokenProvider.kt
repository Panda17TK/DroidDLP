package com.droiddlp.app.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * WebView-backed [PoTokenProvider] skeleton (CLAUDE.md §6 backlog P0-2, step "c").
 *
 * Hosts a hidden, never-attached [WebView] that loads a LOCAL assets page
 * ([HOST_URL]) and establishes the Kotlin<->JS bridge contract used to mint
 * YouTube PoTokens. The real `bgutils-js` bundle (LuanRT/BgUtils) is dropped in
 * a later unit (step "a"); until then the host page reports
 * `BG_BUNDLE_PRESENT = false` and this provider always resolves to `null`.
 *
 * Threading:
 *  - The [WebView] is created and driven on [Dispatchers.Main] (its creation
 *    Looper). It must NEVER be created or touched off the main thread.
 *  - The `@JavascriptInterface` callbacks in [Bridge] arrive on a private
 *    WebView bridge thread, NOT main. They only resume the coroutine (safe from
 *    any thread); the [Bridge] `onReady` hop marshals the entrypoint call back
 *    onto the WebView UI thread via [WebView.post] before calling
 *    `evaluateJavascript`.
 *
 * Lifecycle: a fresh WebView is created per [getPoToken] call and is always
 * destroyed in `finally` under `NonCancellable + Dispatchers.Main`, so teardown
 * runs on the WebView's Looper and survives timeout and caller cancellation.
 *
 * Security: only the bundled local asset is ever loaded; network loads are
 * blocked, file/content access is disabled, and navigation away from [HOST_URL]
 * is denied outright.
 *
 * @param appContext application context (avoids leaking an Activity).
 * @param timeoutMillis upper bound on a single token attempt before resolving
 *   to `null`.
 */
class WebPoTokenProvider(
    appContext: Context,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : PoTokenProvider {
    private val appContext: Context = appContext.applicationContext

    override suspend fun getPoToken(identifier: String): PoTokenResult? =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMillis) {
                runWebViewFlow(identifier)
            }
        }

    /**
     * Create -> load -> bridge -> evaluate flow on the main thread; suspends
     * until JS resolves the contract. Always destroys the WebView, including on
     * timeout/cancellation (teardown re-confined via NonCancellable + Main).
     */
    private suspend fun runWebViewFlow(identifier: String): PoTokenResult? {
        var webView: WebView? = null
        return try {
            suspendCancellableCoroutine { continuation ->
                val resumed = AtomicBoolean(false)

                fun resumeOnce(result: PoTokenResult?) {
                    if (resumed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                val view = createWebView(onTerminalFailure = { resumeOnce(null) })
                webView = view

                view.addJavascriptInterface(
                    Bridge(
                        onReady = {
                            // Arrives on the bridge thread; marshal the
                            // evaluateJavascript call onto the WebView UI thread.
                            view.post {
                                view.evaluateJavascript(
                                    "window.droidDlpGeneratePoToken(${quoteForJs(identifier)});",
                                    null,
                                )
                            }
                        },
                        onResult = { resumeOnce(it) },
                        onUnavailable = { reason ->
                            Log.d(TAG, "PoToken unavailable: $reason")
                            resumeOnce(null)
                        },
                        onError = { message ->
                            Log.w(TAG, "PoToken JS error: $message")
                            resumeOnce(null)
                        },
                    ),
                    BRIDGE_NAME,
                )

                view.loadUrl(HOST_URL)
            }
        } finally {
            // Re-confine to the WebView's Looper and survive cancellation so the
            // WebView is never leaked and never touched off-thread (C1/C2).
            withContext(NonCancellable + Dispatchers.Main) {
                webView?.let(::destroyWebView)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(onTerminalFailure: () -> Unit): WebView =
        WebView(appContext).apply {
            settings.javaScriptEnabled = true // required: the host page runs JS
            settings.blockNetworkLoads = true // no networking now
            settings.allowFileAccess = false // host page is bundled; block file:// traversal
            settings.allowContentAccess = false // block content:// access
            // Never enable allowFileAccessFromFileURLs / allowUniversalAccessFromFileURLs.
            webViewClient = ConfiningWebViewClient(onTerminalFailure)
        }

    private fun destroyWebView(view: WebView) {
        view.removeJavascriptInterface(BRIDGE_NAME) // release the bridge (and its continuation refs)
        view.stopLoading()
        view.destroy()
    }

    /**
     * Confines the WebView to [HOST_URL] and turns load/render failures into a
     * fast `null` instead of a guaranteed timeout (H2) or an app-process crash
     * (H3). All callbacks here run on the WebView's UI (main) thread.
     */
    private class ConfiningWebViewClient(
        private val onTerminalFailure: () -> Unit,
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            // Deny ALL navigation except the exact host asset (S1).
            return request.url.toString() != HOST_URL
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // Only the main-frame asset failing is terminal; ignore subresources.
            if (request.isForMainFrame) {
                Log.w(TAG, "Host page load failed: ${error.errorCode} ${error.description}")
                onTerminalFailure()
            }
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            // Returning true tells the framework we handled it -> no app crash.
            Log.w(TAG, "WebView render process gone (crashed=${detail.didCrash()})")
            onTerminalFailure()
            return true
        }
    }

    /**
     * JS -> Kotlin bridge. Every method is invoked on a private WebView bridge
     * thread, NOT main. The lambdas only resume the coroutine or post to the
     * WebView's own thread — they never touch the WebView directly.
     */
    private class Bridge(
        private val onReady: () -> Unit,
        private val onResult: (PoTokenResult) -> Unit,
        private val onUnavailable: (String) -> Unit,
        private val onError: (String) -> Unit,
    ) {
        @JavascriptInterface
        fun onBridgeReady() = onReady()

        // Always invoked with three args (see HOST contract). visitorData is
        // nullable; JS passes literal null when unknown.
        @JavascriptInterface
        fun onPoTokenResult(
            player: String,
            streaming: String,
            visitorData: String?,
        ) = onResult(PoTokenResult(player, streaming, visitorData))

        @JavascriptInterface
        fun onPoTokenUnavailable(reason: String) = onUnavailable(reason)

        @JavascriptInterface
        fun onPoTokenError(message: String) = onError(message)
    }

    internal companion object {
        private const val TAG = "WebPoTokenProvider"

        /** Local host page; the real page replaces this asset in step "a". */
        const val HOST_URL = "file:///android_asset/potoken/potoken.html"

        /** Global name of the JS->Kotlin bridge object; pinned for step "a". */
        const val BRIDGE_NAME = "DroidDlpBridge"

        /** Default per-attempt timeout. */
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L

        /**
         * Escapes [value] for safe inlining into a JS string literal, using the
         * framework `org.json` quoter (already on the Android classpath; no new
         * dependency). [JSONObject.quote] returns a value WITH surrounding
         * double quotes, e.g. `abc` -> `"abc"`, and escapes `"`, `\`, control
         * chars, and `</` (as `<\/`), which is sufficient for an
         * `evaluateJavascript` source context.
         */
        fun quoteForJs(value: String): String = JSONObject.quote(value)
    }
}
