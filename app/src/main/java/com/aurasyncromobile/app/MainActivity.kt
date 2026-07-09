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
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.aurasyncromobile.app.bridge.AndroidBridgeInjector
import com.aurasyncromobile.app.pos.PosManager
import com.aurasyncromobile.app.printer.PrinterManager
import com.aurasyncromobile.app.ui.theme.AuraSyncroMobileTheme
import com.aurasyncromobile.app.webview.AuraWebViewCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val keepSplashVisible = mutableStateOf(true)
    private lateinit var printerManager: PrinterManager
    private lateinit var posManager: PosManager
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

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
            // Emergenza: se dopo 5 secondi è ancora tutto nero, forziamo la chiusura della splash
            delay(5000)
            if (keepSplashVisible.value) {
                Log.w("AuraMainActivity", "Safety timeout: dismissing splash")
                keepSplashVisible.value = false
            }
        }

        enableEdgeToEdge()

        setContent {
            AuraSyncroMobileTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0D0D0D)) {
                    AuraSyncroWebView(
                        url = getString(R.string.pwa_url),
                        printerManager = printerManager,
                        posManager = posManager,
                        onRequestPermissions = ::requestHardwarePermissions,
                        onShowFileChooser = ::showFileChooser,
                        onFirstPageLoaded = { keepSplashVisible.value = false },
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
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
    printerManager: PrinterManager,
    posManager: PosManager,
    onRequestPermissions: () -> Unit,
    onShowFileChooser: (Intent, ValueCallback<Array<Uri>>) -> Boolean,
    onFirstPageLoaded: () -> Unit,
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isError by remember { mutableStateOf(false) }
    
    val logTag = "AuraWebView"
    val brandGold = Color(0xFFC5A059)
    val brandDark = Color(0xFF0D0D0D)

    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().background(brandDark)) {
        AndroidView(
            factory = { context ->
                val webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    // Slightly lighter than black to see if it's rendering
                    setBackgroundColor(AndroidColor.parseColor("#1A1A1A"))

                    if (BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    // Reset cache once to fix Service Worker issues
                    clearCache(true)

                    AuraWebViewCompat.configure(this, context)

                    settings.apply {
                        userAgentString = "$userAgentString AuraSyncroMobile/1.0"
                        cacheMode = WebSettings.LOAD_DEFAULT
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
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
                        }

                        override fun onPageCommitVisible(view: WebView?, loadedUrl: String?) {
                            super.onPageCommitVisible(view, loadedUrl)
                            dismissSplashOnce()
                        }

                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            view?.let { AndroidBridgeInjector.inject(it, it.context) }
                            dismissSplashOnce()
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                Log.e(logTag, "Main frame error: ${error?.description}")
                                isError = true
                                dismissSplashOnce()
                            }
                        }

                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            view?.reload()
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress / 100f
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (consoleMessage == null) return super.onConsoleMessage(consoleMessage)
                            Log.d(logTag, "[JS] ${consoleMessage.message()}")
                            return true
                        }
                    }

                    addJavascriptInterface(
                        WebAppInterface(
                            context = context,
                            printerManager = printerManager,
                            posManager = posManager,
                            onRequestPermissions = onRequestPermissions,
                        ),
                        "AndroidBridge",
                    )

                    loadUrl(url)
                }

                webViewInstance = webView
                webView
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view ->
                (view as? WebView)?.destroy()
            },
        )

        // Progress Bar overlay
        if (loadProgress > 0f && loadProgress < 1f) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = brandGold,
                trackColor = Color.Transparent,
            )
        }

        // Custom Error Screen
        AnimatedVisibility(
            visible = isError,
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
                        text = "Connessione assente",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Verifica la tua connessione internet e riprova.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { webViewInstance?.reload() },
                        colors = ButtonDefaults.buttonColors(containerColor = brandGold)
                    ) {
                        Text("RIPROVA", color = brandDark)
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
