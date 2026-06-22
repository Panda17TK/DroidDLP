package com.droiddlp.app.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.droiddlp.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * WebView-backed [PoTokenProvider] that mints YouTube PoTokens via the vendored
 * `bgutils-js` bundle (CLAUDE.md §6 P0-3, §7).
 *
 * Network model (NewPipe-style): the hidden WebView performs NO network itself
 * ([WebSettings.setBlockNetworkLoads] stays on and a fail-closed
 * [WebViewClient.shouldInterceptRequest] denies every subresource). The two
 * BotGuard JNN POSTs (Create, GenerateIT) are done natively in Kotlin
 * ([postJnn]) and their results are injected into the page via
 * `evaluateJavascript`. The page is loaded from an inlined HTML string under a
 * virtual `https://www.youtube.com` origin ([loadDataWithBaseURL]) so the
 * BotGuard VM's environment checks pass; the origin grants identity only, never
 * network reach.
 *
 * Threading: the WebView is created and driven on [Dispatchers.Main]; the
 * `@JavascriptInterface` callbacks in [Bridge] arrive on a private bridge thread
 * and only schedule work — [evalOnUi] re-marshals every `evaluateJavascript`
 * onto the WebView's UI thread and early-returns once the view is destroyed.
 *
 * Lifecycle: a fresh WebView is created per [getPoToken] call and is always
 * destroyed in `finally` under `NonCancellable + Dispatchers.Main`; a wall-clock
 * timeout caps a hung or hostile VM.
 *
 * @param appContext application context (avoids leaking an Activity).
 * @param timeoutMillis upper bound on a single token attempt before resolving to
 *   `null`.
 */
class WebPoTokenProvider(
    appContext: Context,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : PoTokenProvider {
    private val appContext: Context = appContext.applicationContext

    override suspend fun getPoToken(
        videoId: String,
        visitorData: String?,
    ): PoTokenResult? =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMillis) {
                coroutineScope { runWebViewFlow(this, videoId, visitorData) }
            }
        }

    private suspend fun runWebViewFlow(
        scope: CoroutineScope,
        videoId: String,
        visitorData: String?,
    ): PoTokenResult? {
        var webView: WebView? = null
        val destroyed = AtomicBoolean(false)
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

                // Guarded UI injection: never touch a destroyed WebView.
                fun evalOnUi(js: String) {
                    if (destroyed.get() || !continuation.isActive) return
                    view.post {
                        if (destroyed.get() || !continuation.isActive) return@post
                        view.evaluateJavascript(js, null)
                    }
                }

                view.addJavascriptInterface(
                    Bridge(
                        onReady = {
                            scope.launch { fetchChallengeAndInject(::evalOnUi, videoId, ::resumeOnce) }
                        },
                        onChallenge = { botguardResponse ->
                            scope.launch {
                                fetchIntegrityTokenAndInject(
                                    ::evalOnUi,
                                    videoId,
                                    visitorData,
                                    botguardResponse,
                                    ::resumeOnce,
                                )
                            }
                        },
                        onResult = { resumeOnce(it) },
                        onUnavailable = { reason ->
                            if (BuildConfig.DEBUG) Log.d(TAG, "PoToken unavailable (debug-only): $reason")
                            resumeOnce(null)
                        },
                        onError = { message ->
                            if (BuildConfig.DEBUG) Log.w(TAG, "PoToken JS error (debug-only): $message")
                            resumeOnce(null)
                        },
                    ),
                    BRIDGE_NAME,
                )

                view.loadDataWithBaseURL(BASE_URL, readHostHtml(), "text/html", "utf-8", null)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                destroyed.set(true)
                webView?.let(::destroyWebView)
            }
        }
    }

    /** Step 1: POST Create, inject the challenge JSON into the page. */
    private suspend fun fetchChallengeAndInject(
        evalOnUi: (String) -> Unit,
        videoId: String,
        resumeOnce: (PoTokenResult?) -> Unit,
    ) {
        val body = JSONArray().put(REQUEST_KEY).toString()
        val challengeJson = postJnn(CREATE_URL, body)
        if (challengeJson == null) {
            resumeOnce(null)
            return
        }
        withContext(Dispatchers.Main) {
            evalOnUi(
                "window.droidDlpSolveChallenge(" +
                    "${quoteForJs(challengeJson)}, ${quoteForJs(videoId)});",
            )
        }
    }

    /** Step 2: POST GenerateIT with the BotGuard response, inject the integrity token. */
    private suspend fun fetchIntegrityTokenAndInject(
        evalOnUi: (String) -> Unit,
        videoId: String,
        visitorData: String?,
        botguardResponse: String,
        resumeOnce: (PoTokenResult?) -> Unit,
    ) {
        if (botguardResponse.isEmpty() || botguardResponse.length > MAX_BOTGUARD_RESPONSE_LENGTH) {
            resumeOnce(null)
            return
        }
        val body = JSONArray().put(REQUEST_KEY).put(botguardResponse).toString()
        val itResponse = postJnn(GENERATE_IT_URL, body)
        if (itResponse == null) {
            resumeOnce(null)
            return
        }
        // GenerateIT returns [integrityToken, ttl, refresh, fallback]; element 0 is the token.
        val integrityToken = runCatching { JSONArray(itResponse).getString(0) }.getOrNull()
        if (integrityToken.isNullOrEmpty()) {
            resumeOnce(null)
            return
        }
        val visitorArg = if (visitorData == null) "null" else quoteForJs(visitorData)
        withContext(Dispatchers.Main) {
            evalOnUi(
                "window.droidDlpMintTokens(" +
                    "${quoteForJs(integrityToken)}, ${quoteForJs(videoId)}, $visitorArg);",
            )
        }
    }

    /**
     * Single hardcoded-URL POST to the JNN family. Bounded read, content-type
     * check, no redirects. Returns the raw response body or `null` on any
     * failure. Never logs the body (token-bearing). Runs on IO.
     */
    private suspend fun postJnn(
        url: String,
        jsonBody: String,
    ): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection =
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        instanceFollowRedirects = false // a JNN redirect is anomalous
                        connectTimeout = HTTP_TIMEOUT_MILLIS
                        readTimeout = HTTP_TIMEOUT_MILLIS
                        doOutput = true
                        // Exact header set the bundle's getHeaders() sends (non-browser).
                        setRequestProperty("Content-Type", "application/json+protobuf")
                        setRequestProperty("x-goog-api-key", GOOG_API_KEY)
                        setRequestProperty("x-user-agent", "grpc-web-javascript/0.1")
                        setRequestProperty("User-Agent", JNN_USER_AGENT)
                    }
                try {
                    connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val contentType = connection.contentType.orEmpty()
                    if (!contentType.contains("json", ignoreCase = true)) {
                        return@runCatching null
                    }
                    readBounded(connection.inputStream)
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { throwable ->
                if (throwable is IOException && BuildConfig.DEBUG) {
                    Log.w(TAG, "JNN POST failed (debug-only): ${throwable.javaClass.simpleName}")
                }
                null
            }
        }

    /** Reads at most [MAX_RESPONSE_BYTES]; returns `null` if the stream exceeds the cap. */
    private fun readBounded(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0
        input.use { stream ->
            while (true) {
                val read = stream.read(chunk)
                if (read == -1) break
                total += read
                if (total > MAX_RESPONSE_BYTES) return null
                buffer.write(chunk, 0, read)
            }
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    /**
     * Reads `potoken.html` and INLINES the bundle + client into it (replacing the
     * two placeholder comments). Under [loadDataWithBaseURL] with an https base, a
     * relative `<script src>` would resolve to a network URL and be blocked, so the
     * scripts MUST be inlined.
     */
    private fun readHostHtml(): String {
        val html = readAsset(HOST_ASSET_PATH)
        val bundle = readAsset(BUNDLE_ASSET_PATH)
        val client = readAsset(CLIENT_ASSET_PATH)
        return html
            .replace(BUNDLE_PLACEHOLDER, bundle)
            .replace(CLIENT_PLACEHOLDER, client)
    }

    private fun readAsset(path: String): String = appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(onTerminalFailure: () -> Unit): WebView =
        WebView(appContext).apply {
            settings.apply {
                javaScriptEnabled = true // VM + bundle require JS
                blockNetworkLoads = true // page makes no network; Kotlin does
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                domStorageEnabled = false
                databaseEnabled = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                setGeolocationEnabled(false)
                safeBrowsingEnabled = false // no URL phone-home; data page never navigates
            }
            // Per-WebView cookie control — do NOT flip the process-global singleton.
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = ConfiningWebViewClient(onTerminalFailure)
        }

    private fun destroyWebView(view: WebView) {
        view.removeJavascriptInterface(BRIDGE_NAME) // release the bridge (and its continuation refs)
        view.stopLoading()
        view.destroy()
    }

    /**
     * Confines the WebView: denies all navigation, fail-closed-blocks every
     * subresource request (the page legitimately fetches nothing — all JS is
     * inlined), and turns load/render failures into a fast terminal `null`.
     */
    private class ConfiningWebViewClient(
        private val onTerminalFailure: () -> Unit,
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = true // the data-loaded page never navigates

        /**
         * Fail-closed egress allowlist. The page is expected to make ZERO
         * subresource requests; anything that reaches here is blocked with an
         * empty 403 and flagged. The main document (loadDataWithBaseURL) does not
         * pass through here, so this never blocks the page itself.
         */
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Blocked unexpected WebView egress (debug-only): ${request.url?.host}")
            }
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                403,
                "Forbidden",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) onTerminalFailure()
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            onTerminalFailure()
            return true // we handled it; the framework must not kill our process
        }
    }

    /**
     * JS -> Kotlin bridge. Every method is invoked on a private WebView bridge
     * thread, NOT main. The lambdas only resume the coroutine or schedule work;
     * they never touch the WebView directly. All methods are inbound-data sinks —
     * none takes a URL or performs an action on the VM's behalf.
     */
    private class Bridge(
        private val onReady: () -> Unit,
        private val onChallenge: (String) -> Unit,
        private val onResult: (PoTokenResult) -> Unit,
        private val onUnavailable: (String) -> Unit,
        private val onError: (String) -> Unit,
    ) {
        @JavascriptInterface
        fun onBridgeReady() = onReady()

        /** JS hands back the BotGuard snapshot response (data sink, not an action). */
        @JavascriptInterface
        fun onChallengeSolved(botguardResponse: String) = onChallenge(botguardResponse)

        /** Always invoked with EXACTLY three args. visitorData is nullable. */
        @JavascriptInterface
        fun onPoTokenResult(
            player: String,
            streaming: String,
            visitorData: String?,
        ) {
            if (player.isEmpty() || streaming.isEmpty() ||
                player.length > MAX_TOKEN_LENGTH || streaming.length > MAX_TOKEN_LENGTH
            ) {
                onError("invalid token payload")
                return
            }
            onResult(PoTokenResult(player, streaming, visitorData))
        }

        @JavascriptInterface
        fun onPoTokenUnavailable(reason: String) = onUnavailable(reason)

        @JavascriptInterface
        fun onPoTokenError(message: String) = onError(message)
    }

    internal companion object {
        private const val TAG = "WebPoTokenProvider"

        /**
         * Virtual document origin for the host page. The BotGuard VM performs
         * location/origin environment checks; an https://www.youtube.com base URL
         * satisfies them. The page makes NO network requests, so this origin
         * grants identity only, never network reach.
         */
        const val BASE_URL = "https://www.youtube.com"

        /** Host asset + the two JS files inlined into it (see readHostHtml). */
        private const val HOST_ASSET_PATH = "potoken/potoken.html"
        private const val BUNDLE_ASSET_PATH = "potoken/bgutils.bundle.js"
        private const val CLIENT_ASSET_PATH = "potoken/potoken-client.js"

        /** Placeholders in potoken.html replaced by the inlined script bodies. */
        private const val BUNDLE_PLACEHOLDER = "/*__BGUTILS_BUNDLE__*/"
        private const val CLIENT_PLACEHOLDER = "/*__POTOKEN_CLIENT__*/"

        /** Global name of the JS<->Kotlin bridge object; pinned since P0-2. */
        const val BRIDGE_NAME = "DroidDlpBridge"

        /**
         * Overall per-attempt wall-clock budget. Must exceed worst-case
         * (2 * HTTP_TIMEOUT_MILLIS) + VM solve headroom. 6s + 6s = 12s < 15s.
         */
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        private const val HTTP_TIMEOUT_MILLIS = 6_000

        /**
         * YouTube's well-known *public web* BotGuard request key (LuanRT/BgUtils,
         * NewPipe). NOTE: this is NOT embedded in bgutils.bundle.js — it is the
         * caller-supplied bgConfig.requestKey. Supplied here as the established
         * web value. Not a private credential.
         */
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"

        /**
         * YouTube's PUBLIC web Innertube API key — the exact constant the vendored
         * bgutils.bundle.js itself ships and sends on every JNN POST. Present in
         * every youtube.com page load; NOT a secret credential. Documented
         * exception to the no-hardcoded-secrets checklist (CLAUDE.md §9).
         */
        private const val GOOG_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"

        /** Exact UA the bundle's getHeaders() adds in non-browser contexts. */
        private const val JNN_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36(KHTML, like Gecko)"

        /** First-party JNN endpoints (useYouTubeAPI=true family). */
        private const val CREATE_URL = "$BASE_URL/api/jnn/v1/Create"
        private const val GENERATE_IT_URL = "$BASE_URL/api/jnn/v1/GenerateIT"

        /** Bound the opaque BotGuard snapshot (multi-KB is normal). */
        private const val MAX_BOTGUARD_RESPONSE_LENGTH = 100_000

        /** Bound the *output* tokens only (PoTokens are a few hundred bytes). */
        private const val MAX_TOKEN_LENGTH = 8_192

        /** Hard cap on any JNN HTTP response read, to prevent OOM DoS. */
        private const val MAX_RESPONSE_BYTES = 512 * 1024

        /**
         * Escapes [value] for safe inlining into a JS string literal via the
         * framework `org.json` quoter (no new dependency). Returns the value WITH
         * surrounding double quotes and all dangerous characters escaped.
         */
        fun quoteForJs(value: String): String = JSONObject.quote(value)
    }
}
