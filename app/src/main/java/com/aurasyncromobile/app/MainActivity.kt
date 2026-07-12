package com.aurasyncromobile.app

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.animation.doOnEnd
import androidx.core.graphics.toColorInt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
    private val keepSplashVisible = mutableStateOf(value = true)
    private lateinit var printerManager: PrinterManager
    private lateinit var posManager: PosManager
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var auraWebView: WebView? = null

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
        splashScreen.setKeepOnScreenCondition {
            keepSplashVisible.value
        }

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = ObjectAnimator.ofFloat(
                splashScreenView.view,
                View.ALPHA,
                1f,
                0f,
            )
            fadeOut.interpolator = AccelerateInterpolator()
            fadeOut.duration = 250L
            fadeOut.doOnEnd { splashScreenView.remove() }
            fadeOut.start()
        }

        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // Emergenza: se dopo 4 secondi è ancora tutto nero, forziamo la chiusura della splash
            // Riduciamo il tempo e assicuriamoci che avvenga anche se qualcosa va storto
            delay(4.seconds)
            if (keepSplashVisible.value) {
                Log.w("AuraMainActivity", "Safety timeout: dismissing splash")
                keepSplashVisible.value = false
            }
        }

        enableEdgeToEdge()

        setContent {
            AuraSyncroMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AuraSyncroWebView(
                        url = getString(R.string.pwa_url),
                        loginUrl = getString(R.string.pwa_url),
                        baseUrl = getString(R.string.pwa_base_url),
                        dashboardUrl = getString(R.string.pwa_dashboard_url),
                        printerManager = printerManager,
                        posManager = posManager,
                        onRequestPermissions = ::requestHardwarePermissions,
                        onShowFileChooser = ::showFileChooser,
                        onWebViewCreated = { webView -> auraWebView = webView },
                        onWebViewDestroyed = { auraWebView = null },
                        onFirstPageLoaded = {
                            Log.i("AuraMainActivity", "WebView first page loaded, dismissing splash")
                            keepSplashVisible.value = false
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        auraWebView?.apply {
            onResume()
            resumeTimers()
        }
    }

    override fun onPause() {
        auraWebView?.apply {
            pauseTimers()
            onPause()
        }
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        auraWebView?.destroy()
        auraWebView = null
        super.onDestroy()
    }

    private fun requestHardwarePermissions() {
        permissionLauncher.launch(printerManager.requiredPermissions())
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuraSyncroWebView(
    url: String,
    loginUrl: String,
    baseUrl: String,
    dashboardUrl: String,
    printerManager: PrinterManager,
    posManager: PosManager,
    onRequestPermissions: () -> Unit,
    onShowFileChooser: (Intent, ValueCallback<Array<Uri>>) -> Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onWebViewDestroyed: () -> Unit,
    onFirstPageLoaded: () -> Unit,
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isError by remember { mutableStateOf(false) }
    var isStuck by remember { mutableStateOf(false) }
    var blankRecoveryAttempts by remember { mutableIntStateOf(0) }
    var currentSpaPath by remember { mutableStateOf("/login") }
    var isRecoveryNavigation by remember { mutableStateOf(false) }

    val logTag = "AuraWebView"
    val brandGold = Color(0xFFC5A059)
    val brandDark = Color(0xFF0D0D0D)

    fun updateCanGoBack(view: WebView?) {
        canGoBack = view?.canGoBack() == true
    }

    fun isLoginPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return true
        return path == "/" || path.equals("/login", ignoreCase = true)
    }

    fun isLoginUrl(pageUrl: String?): Boolean {
        if (pageUrl.isNullOrBlank()) return false
        return pageUrl.contains("/login", ignoreCase = true)
    }

    fun loadTargetPath(view: WebView, targetPath: String, recover: Boolean = false) {
        val normalizedPath = targetPath.ifBlank { "/dashboard" }
        if (recover) {
            isRecoveryNavigation = true
            blankRecoveryAttempts++
            Log.w(logTag, "Recovery verso $normalizedPath (tentativo $blankRecoveryAttempts)")
        }
        isStuck = false
        val bustUrl = "$baseUrl$normalizedPath?_aura=${System.currentTimeMillis()}"
        view.loadUrl(bustUrl)
    }

    fun resetToLogin(view: WebView) {
        blankRecoveryAttempts = 0
        isRecoveryNavigation = false
        isStuck = false
        isError = false
        currentSpaPath = "/login"
        AuraWebViewCompat.purgeCaches(view)
        view.loadUrl(loginUrl)
    }

    fun checkBlankPage(view: WebView?, loadedUrl: String?, delayMs: Long = 8_000L) {
        if (view == null) return
        val path = currentSpaPath
        if (isLoginPath(path)) return

        view.postDelayed({
            view.evaluateJavascript(
                """
                (function() {
                  if (window.__auraIsStuckLoading) return window.__auraIsStuckLoading() ? 'stuck' : 'ok';
                  var body = document.body;
                  if (!body) return 'no-body';
                  var text = (body.innerText || '').replace(/\s+/g, ' ').trim();
                  var loading = /caricamento in corso|loading/i.test(text);
                  var hasApp = !!document.querySelector('main, nav, [role="main"], [data-testid], .app-shell, #root > *:not(script):not(style)');
                  if (loading && !hasApp) return 'stuck-loading';
                  if (text.length < 8 && !hasApp) return 'blank';
                  return 'ok';
                })()
                """.trimIndent(),
            ) { result ->
                if (result == "\"ok\"" || result == "null") {
                    isStuck = false
                    return@evaluateJavascript
                }
                Log.w(logTag, "Pagina bloccata ($result) path=$path url=$loadedUrl tentativi=$blankRecoveryAttempts")
                when {
                    blankRecoveryAttempts < 2 -> loadTargetPath(view, path, recover = true)
                    else -> {
                        isStuck = true
                        onFirstPageLoaded()
                    }
                }
            }
        }, delayMs)
    }

    fun handleCompatEvent(event: String, detailJson: String) {
        Log.i(logTag, "Compat event: $event detail=$detailJson")
        val view = webViewInstance ?: return
        when (event) {
            "login-success", "route-change" -> {
                val path = runCatching {
                    org.json.JSONObject(detailJson).optString("path", "/dashboard")
                }.getOrDefault("/dashboard")
                currentSpaPath = path
                if (!isRecoveryNavigation) {
                    blankRecoveryAttempts = 0
                }
                checkBlankPage(view, view.url, delayMs = 10_000L)
            }
            "stuck-loading" -> {
                val path = runCatching {
                    org.json.JSONObject(detailJson).optString("path", "/dashboard")
                }.getOrDefault("/dashboard")
                currentSpaPath = path
                if (blankRecoveryAttempts < 2) {
                    loadTargetPath(view, path, recover = true)
                } else {
                    isStuck = true
                    onFirstPageLoaded()
                }
            }
        }
    }

    // Monitor SPA: dopo il login la URL nativa resta /login ma pathname JS cambia
    androidx.compose.runtime.LaunchedEffect(webViewInstance) {
        val view = webViewInstance ?: return@LaunchedEffect
        while (true) {
            delay(2.seconds)
            view.evaluateJavascript("window.location.pathname || '/';") { pathResult ->
                val path = pathResult?.trim('"') ?: return@evaluateJavascript
                if (path != currentSpaPath) {
                    currentSpaPath = path
                    Log.d(logTag, "SPA path cambiato: $path")
                    if (!isLoginPath(path) && !isRecoveryNavigation) {
                        blankRecoveryAttempts = 0
                        checkBlankPage(view, view.url, delayMs = 10_000L)
                    }
                }
            }
        }
    }

    // Gestione timeout caricamento
    androidx.compose.runtime.LaunchedEffect(loadProgress) {
        if (loadProgress > 0f && loadProgress < 0.1f) {
            delay(15.seconds)
            if (loadProgress < 0.1f && !isError) {
                Log.e(logTag, "Caricamento troppo lento o bloccato")
                isError = true
                onFirstPageLoaded()
            }
        }
    }

    BackHandler(enabled = true) {
        val view = webViewInstance
        when {
            view == null -> Unit
            isError || isStuck -> resetToLogin(view)
            view.canGoBack() && !isLoginPath(currentSpaPath) -> view.goBack()
            !isLoginPath(currentSpaPath) || !isLoginUrl(view.url) -> resetToLogin(view)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(brandDark)) {
        AndroidView(
            factory = { context ->
                val webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // NON usare LAYER_TYPE_HARDWARE: su molti GPU (Mali, MediaTek) causa schermo nero
                    setLayerType(View.LAYER_TYPE_NONE, null)

                    setBackgroundColor("#0F0F0F".toColorInt())

                    if (BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    // NON puliamo la cache ad ogni avvio nel factory, 
                    // altrimenti il login potrebbe perdere lo stato della sessione appena creata
                    // clearCache(true) // Rimosso da qui

                    AuraWebViewCompat.configure(this, context)

                    settings.apply {
                        // Usiamo un UserAgent standard ma aggiungiamo il nostro suffisso in modo pulito
                        val defaultUA = userAgentString
                        userAgentString = "$defaultUA AuraSyncroMobile/1.0"
                        
                        allowFileAccess = true
                        allowContentAccess = true
                        domStorageEnabled = true
                        
                        // Importante per i login che usano pop-up o redirect complessi
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = true
                    }

                    webViewClient = object : WebViewClient() {
                        private var isFirstLoad = true

                        private fun dismissSplashOnce() {
                            if (isFirstLoad) {
                                isFirstLoad = false
                                Log.i(logTag, "Page visible, dismissing splash")
                                onFirstPageLoaded()
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val uri = request.url
                            val scheme = uri.scheme?.lowercase()
                            if (scheme == "http" || scheme == "https") {
                                return false
                            }
                            return openExternalUrl(context, uri)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isError = false
                            isStuck = false
                            if (!isRecoveryNavigation) {
                                blankRecoveryAttempts = 0
                            }
                            Log.d(logTag, "Caricamento iniziato: $url recovery=$isRecoveryNavigation")
                            view?.let {
                                AuraWebViewCompat.injectCompatScript(it, it.context)
                            }
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            updateCanGoBack(view)
                        }

                        override fun onPageCommitVisible(view: WebView?, loadedUrl: String?) {
                            super.onPageCommitVisible(view, loadedUrl)
                            dismissSplashOnce()
                        }

                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            isRecoveryNavigation = false
                            view?.let {
                                AuraWebViewCompat.injectCompatScript(it, it.context)
                                AndroidBridgeInjector.inject(it, it.context)
                            }
                            dismissSplashOnce()
                            updateCanGoBack(view)
                            Log.d(logTag, "Pagina caricata: $loadedUrl - canGoBack: $canGoBack")
                            CookieManager.getInstance().flush()
                            checkBlankPage(view, loadedUrl)
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            // Alcuni errori minori non devono bloccare l'app
                            if (request?.isForMainFrame == true) {
                                val errorDesc = error?.description ?: "Error"
                                Log.e(logTag, "Main frame error: $errorDesc")
                                isError = true
                                dismissSplashOnce()
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request?.isForMainFrame == true) {
                                Log.e(logTag, "HTTP Error: ${errorResponse?.statusCode}")
                                // Alcuni server rispondono con 4xx/5xx ma mostrano comunque una pagina
                                // Se il caricamento fallisce drasticamente, lo segnaliamo
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            Log.e(logTag, "SSL Error: $error")
                            // In caso di errore SSL (certificato scaduto, etc) il WebView si blocca.
                            // Mostriamo la schermata di errore.
                            isError = true
                            dismissSplashOnce()
                            handler?.cancel() // Sicurezza: annulla la connessione non sicura
                        }

                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            Log.w(logTag, "WebView process gone, reloading...")
                            view?.reload()
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress / 100f
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            val intent = fileChooserParams?.createIntent()
                            return if (intent != null && filePathCallback != null) {
                                onShowFileChooser(intent, filePathCallback)
                            } else {
                                false
                            }
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (consoleMessage == null) return super.onConsoleMessage(consoleMessage)
                            Log.d(logTag, "[JS] ${consoleMessage.messageLevel()}: ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                            return true
                        }
                    }

                    addJavascriptInterface(
                        WebAppInterface(
                            context = context,
                            printerManager = printerManager,
                            posManager = posManager,
                            onRequestPermissions = onRequestPermissions,
                            onCompatEvent = ::handleCompatEvent,
                        ),
                        "AndroidBridge",
                    )

                    loadUrl(url)
                }

                webViewInstance = webView
                onWebViewCreated(webView)
                webView
            },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            onRelease = { view ->
                onWebViewDestroyed()
                view.destroy()
            },
        )

        // Progress Bar overlay
        if (loadProgress < 1f) {
            LinearProgressIndicator(
                progress = { if (loadProgress <= 0f) 0.1f else loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = brandGold,
                trackColor = Color.Transparent,
            )
        }

        // Schermata errore / pagina bloccata
        AnimatedVisibility(
            visible = isError || isStuck,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brandDark)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = brandGold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isError) "Connessione assente" else "Caricamento bloccato",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = if (isError) {
                            "Verifica la tua connessione internet e riprova."
                        } else {
                            "L'app non riesce a caricare la pagina. Puoi riprovare o tornare al login."
                        },
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val view = webViewInstance
                            if (view != null) {
                                blankRecoveryAttempts = 0
                                isError = false
                                isStuck = false
                                loadTargetPath(view, currentSpaPath.ifBlank { "/dashboard" }, recover = false)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = brandGold)
                    ) {
                        Text("RIPROVA", color = brandDark)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            webViewInstance?.let(::resetToLogin)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = brandGold,
                        )
                    ) {
                        Text("TORNA AL LOGIN")
                    }
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
