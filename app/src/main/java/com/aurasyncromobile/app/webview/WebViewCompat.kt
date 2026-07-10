package com.aurasyncromobile.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.Collections
import java.util.WeakHashMap

object AuraWebViewCompat {
    private const val COMPAT_SCRIPT = "webview-compat.js"
    private val configuredWebViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    private const val PURGE_CACHE_SCRIPT = """
        (function() {
          if ('caches' in window) {
            caches.keys().then(function(keys) {
              return Promise.all(keys.map(function(k) { return caches.delete(k); }));
            });
          }
          if ('serviceWorker' in navigator) {
            navigator.serviceWorker.getRegistrations().then(function(regs) {
              regs.forEach(function(r) { r.unregister(); });
            });
          }
        })();
    """

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, context: Context) {
        if (configuredWebViews.contains(webView)) return

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController.getInstance().apply {
                serviceWorkerWebSettings.apply {
                    allowContentAccess = true
                    allowFileAccess = true
                    blockNetworkLoads = false
                }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                setRenderPriority(WebSettings.RenderPriority.HIGH)
            }
        }

        webView.apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(android.graphics.Color.parseColor("#0F0F0F"))
        }

        installDocumentStartScript(webView, context)
        configuredWebViews.add(webView)
    }

    private fun installDocumentStartScript(webView: WebView, context: Context) {
        val script = readCompatScript(context)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                script,
                setOf("*"),
            )
        }
    }

    fun injectCompatScript(webView: WebView, context: Context) {
        webView.evaluateJavascript(readCompatScript(context), null)
    }

    fun purgeCaches(webView: WebView) {
        webView.evaluateJavascript(PURGE_CACHE_SCRIPT, null)
        webView.clearCache(true)
    }

    fun clearWebStorageOnAppUpdate(context: Context) {
        val prefs = context.getSharedPreferences("aura_webview", Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt("last_version_code", 0)
        if (lastVersion == com.aurasyncromobile.app.BuildConfig.VERSION_CODE) return

        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        prefs.edit()
            .putInt("last_version_code", com.aurasyncromobile.app.BuildConfig.VERSION_CODE)
            .apply()
    }

    private fun readCompatScript(context: Context): String {
        return context.assets.open(COMPAT_SCRIPT).bufferedReader().use { it.readText() }
    }
}
