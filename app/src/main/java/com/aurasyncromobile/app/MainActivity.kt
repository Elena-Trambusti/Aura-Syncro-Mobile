package com.aurasyncromobile.app

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.animation.doOnEnd
import androidx.core.graphics.toColorInt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.aurasyncromobile.app.bridge.AndroidBridgeInjector
import com.aurasyncromobile.app.bridge.BridgeEvents
import com.aurasyncromobile.app.hardware.HardwareConfigStore
import com.aurasyncromobile.app.pos.PosManager
import com.aurasyncromobile.app.printer.PrinterManager
import com.aurasyncromobile.app.webview.AuraWebViewCompat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val keepSplashVisible = mutableStateOf(true)
    private lateinit var printerManager: PrinterManager
    private lateinit var posManager: PosManager
    private lateinit var hardwareConfigStore: HardwareConfigStore
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var webView: WebView

    private var lastNativeNavigationPath: String? = null
    private var lastNativeNavigationAt: Long = 0L
    private var webPageReady = false
    private val pendingBridgeEvents = mutableListOf<Pair<String, org.json.JSONObject>>()

    private val logTag = "AuraWebView"
    private val loginUrl by lazy { getString(R.string.pwa_url) }
    private val baseUrl by lazy { getString(R.string.pwa_base_url) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (!::webView.isInitialized) return@registerForActivityResult
            val granted = results.values.all { it }
            dispatchBridgeEvent(
                "aurasyncro-permissions-result",
                org.json.JSONObject().put("granted", granted),
            )
        }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback
            fileChooserCallback = null
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            callback?.onReceiveValue(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        printerManager = PrinterManager(this)
        posManager = PosManager(this)
        hardwareConfigStore = HardwareConfigStore(this)

        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashVisible.value }
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f).apply {
                interpolator = AccelerateInterpolator()
                duration = 250L
                doOnEnd { splashScreenView.remove() }
                start()
            }
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLuxurySystemBars()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        webView.canGoBack() -> webView.goBack()
                        else -> webView.loadUrl(loginUrl)
                    }
                }
            },
        )

        val root = FrameLayout(this).apply {
            setBackgroundColor("#0D0D0D".toColorInt())
        }
        webView = createWebView()
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        setContentView(root)

        lifecycleScope.launch {
            delay(3.seconds)
            keepSplashVisible.value = false
        }

        webView.post {
            webView.loadUrl(loginUrl)
            handlePaymentDeepLink(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePaymentDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
            notifyAppResumed()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLuxurySystemBars() {
        window.statusBarColor = "#0D0D0D".toColorInt()
        window.navigationBarColor = "#0D0D0D".toColorInt()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun navigateToPath(path: String) {
        val normalized = path.ifBlank { "/dashboard" }.let { if (it.startsWith("/")) it else "/$it" }
        val now = System.currentTimeMillis()
        if (normalized == lastNativeNavigationPath && now - lastNativeNavigationAt < 5_000L) {
            Log.w(logTag, "Navigazione duplicata ignorata: $normalized")
            return
        }
        lastNativeNavigationPath = normalized
        lastNativeNavigationAt = now
        webPageReady = false
        webView.stopLoading()
        webView.loadUrl(appUrl(normalized))
        Log.i(logTag, "Navigazione nativa verso $normalized")
    }

    private fun appUrl(path: String): String {
        val normalized = path.ifBlank { "/dashboard" }.let { if (it.startsWith("/")) it else "/$it" }
        return "$baseUrl$normalized?pwa=1"
    }

    private fun dismissSplash() {
        keepSplashVisible.value = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(this).apply {
            setLayerType(View.LAYER_TYPE_NONE, null)

            AuraWebViewCompat.configure(this, this@MainActivity)

            settings.apply {
                val defaultUa = userAgentString
                userAgentString = "$defaultUa AuraSyncroMobile/1.0"
                allowFileAccess = true
                allowContentAccess = true
                domStorageEnabled = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = true
            }

            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return false
                    return openExternalUrl(this@MainActivity, request.url)
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    dismissSplash()
                    Log.i(logTag, "Pagina visibile: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.let {
                        AuraWebViewCompat.injectCompatScript(it, this@MainActivity)
                        AndroidBridgeInjector.inject(it, this@MainActivity)
                        webPageReady = true
                        flushPendingBridgeEvents(it)
                    }
                    dismissSplash()
                    CookieManager.getInstance().flush()
                    Log.i(logTag, "Pagina caricata: $url")
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame != true) return
                    Log.e(logTag, "Errore main frame: ${error?.description} url=${request.url}")
                    view?.postDelayed({ view.reload() }, 1_000L)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        Log.w(logTag, "HTTP ${errorResponse?.statusCode} su ${request.url}")
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    Log.e(logTag, "SSL error: $error — procedo comunque")
                    handler?.proceed()
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    Log.w(logTag, "Render process gone - reload")
                    view?.reload()
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    val intent = fileChooserParams?.createIntent()
                    return if (intent != null && filePathCallback != null) {
                        showFileChooser(intent, filePathCallback)
                    } else {
                        false
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d(logTag, "[JS] ${it.messageLevel()}: ${it.message()}")
                    }
                    return true
                }
            }

            addJavascriptInterface(
                WebAppInterface(
                    context = this@MainActivity,
                    printerManager = printerManager,
                    posManager = posManager,
                    hardwareConfigStore = hardwareConfigStore,
                    onRequestPermissions = ::requestHardwarePermissions,
                    onCompatEvent = { event, detail ->
                        runOnUiThread {
                            handleCompatEvent(event, detail)
                        }
                    },
                    onBridgeEvent = { eventName, detail ->
                        runOnUiThread {
                            dispatchBridgeEvent(eventName, detail)
                        }
                    },
                ),
                "AndroidBridge",
            )
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.pauseTimers()
            webView.onPause()
        }
        CookieManager.getInstance().flush()
        super.onPause()
    }

    private fun handlePaymentDeepLink(intent: Intent?) {
        if (!::webView.isInitialized) return
        val uri = intent?.data ?: return
        val parsed = posManager.parsePaymentResultUri(uri) ?: return

        setIntent(
            Intent(intent).apply {
                data = null
                action = Intent.ACTION_MAIN
            },
        )

        val orderId = parsed.optString("orderId")
        val status = parsed.optString("status", "unknown")
        val txId = parsed.optString("txId").ifBlank { null }

        val result = runCatching {
            posManager.completePayment(orderId, status, txId, source = "deep-link")
        }.getOrElse {
            parsed
        }

        dispatchBridgeEvent("aurasyncro-payment-result", result)
        Log.i(logTag, "Pagamento ricevuto via deep link: $result")
    }

    private fun notifyAppResumed() {
        val pending = posManager.getPendingPayment()
        val detail = org.json.JSONObject().put("pendingPayment", pending != null)
        if (pending != null) {
            detail.put("payment", pending.toJson())
        }
        dispatchBridgeEvent("aurasyncro-app-resumed", detail)
    }

    private fun dispatchBridgeEvent(eventName: String, detail: org.json.JSONObject) {
        if (!::webView.isInitialized) return
        if (!webPageReady) {
            pendingBridgeEvents += eventName to detail
            return
        }
        BridgeEvents.dispatch(webView, eventName, detail)
    }

    private fun flushPendingBridgeEvents(webView: WebView) {
        if (pendingBridgeEvents.isEmpty()) return
        val events = pendingBridgeEvents.toList()
        pendingBridgeEvents.clear()
        events.forEach { (eventName, detail) ->
            BridgeEvents.dispatch(webView, eventName, detail)
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun requestHardwarePermissions() {
        permissionLauncher.launch(printerManager.requiredPermissions())
    }

    private fun handleCompatEvent(event: String, detail: String) {
        Log.i(logTag, "Compat: $event $detail")
        val path = runCatching {
            org.json.JSONObject(detail).optString("path", "/dashboard")
        }.getOrDefault("/dashboard")

        when (event) {
            "login-success", "navigate" -> navigateToPath(path)
        }
    }

    private fun showFileChooser(intent: Intent, callback: ValueCallback<Array<Uri>>): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback
        return try {
            fileChooserLauncher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            fileChooserCallback = null
            callback.onReceiveValue(null)
            false
        }
    }
}

private fun openExternalUrl(context: android.content.Context, uri: Uri): Boolean {
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
