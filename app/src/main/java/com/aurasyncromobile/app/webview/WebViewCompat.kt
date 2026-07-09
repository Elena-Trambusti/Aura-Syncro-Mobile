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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object AuraWebViewCompat {
    private const val COMPAT_SCRIPT = "webview-compat.js"
    private const val ENABLE_COMPAT_SCRIPT = false
    private var documentStartScriptInstalled = false

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, context: Context) {
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
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // Stability & Compatibility Settings
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100 
            useWideViewPort = true
            loadWithOverviewMode = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        // Clean UI - No Scrollbars
        webView.apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        if (ENABLE_COMPAT_SCRIPT) {
            installDocumentStartScript(webView, context)
        }
    }

    private fun installDocumentStartScript(webView: WebView, context: Context) {
        if (documentStartScriptInstalled) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return

        val script = context.assets.open(COMPAT_SCRIPT).bufferedReader().use { it.readText() }
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            script,
            setOf("*"),
        )
        documentStartScriptInstalled = true
    }

    fun clearWebStorageOnAppUpdate(context: Context) {
        val prefs = context.getSharedPreferences("aura_webview", Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt("last_version_code", 0)
        if (lastVersion == com.aurasyncromobile.app.BuildConfig.VERSION_CODE) return

        WebStorage.getInstance().deleteAllData()
        prefs.edit()
            .putInt("last_version_code", com.aurasyncromobile.app.BuildConfig.VERSION_CODE)
            .apply()
    }

    fun injectCompatScript(webView: WebView, context: Context) {
        if (!ENABLE_COMPAT_SCRIPT) return
        val script = context.assets.open(COMPAT_SCRIPT).bufferedReader().use { it.readText() }
        webView.evaluateJavascript(script, null)
    }
}
