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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.graphics.toColorInt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.aurasyncromobile.app.bridge.AndroidBridgeInjector
import com.aurasyncromobile.app.pos.PosManager
import com.aurasyncromobile.app.printer.PrinterManager
import com.aurasyncromobile.app.ui.theme.AuraSyncroMobileTheme
import com.aurasyncromobile.app.webview.AuraWebViewCompat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val keepSplashVisible = mutableStateOf(true)
    private lateinit var printerManager: PrinterManager
    private lateinit var posManager: PosManager
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var webView: WebView
    private lateinit var overlay: ComposeView

    private val overlayState = mutableStateOf(OverlayUiState())

    private var lastNativeNavigationPath: String? = null
    private var lastNativeNavigationAt: Long = 0L

    private val logTag = "AuraWebView"
    private val loginUrl by lazy { getString(R.string.pwa_url) }
    private val baseUrl by lazy { getString(R.string.pwa_base_url) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

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
                    handleBackPress()
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

        val overlay = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setContent {
                AuraSyncroMobileTheme {
                    val state by overlayState
                    WebViewOverlay(
                        showError = state.showError,
                        errorTitle = state.errorTitle,
                        loadProgress = state.loadProgress,
                    )
                }
            }
        }
        this.overlay = overlay
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        setContentView(root)
        syncOverlayInteraction()

        lifecycleScope.launch {
            delay(3.seconds)
            keepSplashVisible.value = false
        }

        lifecycleScope.launch {
            delay(15.seconds)
            val state = overlayState.value
            if (state.loadProgress <= 0.05f && !state.showError) {
                Log.w(logTag, "Watchdog: caricamento non partito")
                showBlockedError()
            }
        }

        webView.post { webView.loadUrl(loginUrl) }
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

    private fun syncOverlayInteraction() {
        if (!::overlay.isInitialized) return
        val blockTouches = overlayState.value.showError
        overlay.isClickable = blockTouches
        overlay.isFocusable = blockTouches
        if (blockTouches) {
            overlay.setOnTouchListener(null)
        } else {
            overlay.setOnTouchListener { _, event ->
                webView.dispatchTouchEvent(event)
                true
            }
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

        overlayState.value = overlayState.value.copy(
            showError = false,
            loadProgress = 0f,
        )
        webView.stopLoading()
        webView.loadUrl("$baseUrl$normalized")
        Log.i(logTag, "Navigazione nativa verso $normalized")
    }

    private fun handleBackPress() {
        val state = overlayState.value
        Log.i(logTag, "Back premuto - error=${state.showError} canGoBack=${webView.canGoBack()}")
        when {
            state.showError -> goToLogin()
            webView.canGoBack() -> webView.goBack()
            else -> showBlockedError()
        }
    }

    private fun dismissSplash() {
        keepSplashVisible.value = false
    }

    private fun showBlockedError(title: String = "Caricamento bloccato") {
        overlayState.value = overlayState.value.copy(
            showError = true,
            errorTitle = title,
        )
        dismissSplash()
        syncOverlayInteraction()
    }

    private fun goToLogin() {
        lastNativeNavigationPath = null
        overlayState.value = overlayState.value.copy(
            showError = false,
            loadProgress = 0f,
        )
        syncOverlayInteraction()
        webView.stopLoading()
        webView.loadUrl(loginUrl)
    }

    private fun updateLoadProgress(progress: Float) {
        overlayState.value = overlayState.value.copy(
            loadProgress = progress,
            showError = if (progress >= 1f) false else overlayState.value.showError,
        )
        syncOverlayInteraction()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(this).apply {
            // CRITICO: mai HARDWARE su Samsung/Mali — schermo nero totale
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
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.let {
                        AuraWebViewCompat.injectCompatScript(it, this@MainActivity)
                        AndroidBridgeInjector.inject(it, this@MainActivity)
                        hidePwaRecoveryOverlay(it)
                        if (url?.contains("/login", ignoreCase = true) == true) {
                            it.post { it.clearHistory() }
                        }
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
                    if (request?.isForMainFrame == true) {
                        Log.e(logTag, "Errore main frame: ${error?.description}")
                        showBlockedError("Connessione assente")
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    Log.e(logTag, "SSL error: $error")
                    showBlockedError("Connessione non sicura")
                    handler?.cancel()
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    Log.w(logTag, "Render process gone - reload")
                    view?.reload()
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    updateLoadProgress(newProgress / 100f)
                }

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
                    onRequestPermissions = ::requestHardwarePermissions,
                    onCompatEvent = { event, detail ->
                        runOnUiThread {
                            handleCompatEvent(event, detail)
                        }
                    },
                ),
                "AndroidBridge",
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
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

    private fun hidePwaRecoveryOverlay(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function() {
              if (window.__auraHideRecoveryUiAggressive) return;
              window.__auraHideRecoveryUiAggressive = true;
              function hasTargetText(el) {
                var text = (el.innerText || el.textContent || '').toLowerCase();
                return text.indexOf('ripristina app') !== -1 || 
                       text.indexOf('restore app') !== -1 || 
                       text.indexOf('torna al login') !== -1;
              }
              function hideNow() {
                var elements = document.querySelectorAll('button, a, [role="button"], div, section, span');
                for (var i = 0; i < elements.length; i++) {
                  var el = elements[i];
                  if (hasTargetText(el)) {
                    var style = window.getComputedStyle(el);
                    // Nascondi se è un pulsante o se è parte di un overlay fisso
                    if (el.tagName === 'BUTTON' || el.tagName === 'A' || style.position === 'fixed' || style.position === 'sticky') {
                      el.style.setProperty('display', 'none', 'important');
                      el.style.setProperty('visibility', 'hidden', 'important');
                      el.style.setProperty('opacity', '0', 'important');
                      el.style.setProperty('pointer-events', 'none', 'important');
                    }
                  }
                }
              }
              hideNow();
              var observer = new MutationObserver(hideNow);
              observer.observe(document.documentElement, { childList: true, subtree: true });
            })();
            """.trimIndent(),
            null,
        )
    }
}

private data class OverlayUiState(
    val showError: Boolean = false,
    val errorTitle: String = "Caricamento bloccato",
    val loadProgress: Float = 0f,
)

@Composable
private fun WebViewOverlay(
    showError: Boolean,
    errorTitle: String,
    loadProgress: Float,
) {
    val brandGold = Color(0xFFC5A059)
    val brandDark = Color(0xFF0D0D0D)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (showError) Modifier.background(brandDark) else Modifier),
    ) {
        if (!showError && loadProgress in 0f..0.99f) {
            LinearProgressIndicator(
                progress = { if (loadProgress <= 0f) 0.08f else loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = brandGold,
                trackColor = Color.Transparent,
            )
        }

        if (showError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = brandGold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Text(
                        text = "Premi Indietro per uscire da questa schermata.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
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
