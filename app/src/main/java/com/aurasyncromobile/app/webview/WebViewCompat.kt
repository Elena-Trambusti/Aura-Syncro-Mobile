package com.aurasyncromobile.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.ServiceWorkerController
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

object AuraWebViewCompat {
    private const val COMPAT_SCRIPT = "webview-compat.js"
    private val configuredWebViews = mutableSetOf<WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, context: Context) {
        if (webView in configuredWebViews) return

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        ServiceWorkerController.getInstance().serviceWorkerWebSettings.apply {
            allowContentAccess = true
            allowFileAccess = true
            blockNetworkLoads = false
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
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            safeBrowsingEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                offscreenPreRaster = true
            }
        }

        webView.apply {
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor("#0D0D0D".toColorInt())
        }

        configuredWebViews.add(webView)
    }

    fun injectCompatScript(webView: WebView, context: Context) {
        try {
            val script = context.assets.open(COMPAT_SCRIPT).bufferedReader().use { it.readText() }
            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            android.util.Log.e("AuraWebViewCompat", "Compat script injection failed", e)
        }
    }

    fun clearWebStorageOnAppUpdate(context: Context) {
        val prefs = context.getSharedPreferences("aura_webview", Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt("last_version_code", 0)
        if (lastVersion == com.aurasyncromobile.app.BuildConfig.VERSION_CODE) return

        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        prefs.edit(commit = false) {
            putInt("last_version_code", com.aurasyncromobile.app.BuildConfig.VERSION_CODE)
        }
    }
}
