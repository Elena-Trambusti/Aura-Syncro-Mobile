package com.aurasyncromobile.app.bridge

import android.content.Context
import android.webkit.WebView

object AndroidBridgeInjector {
    private const val ASSET_FILE = "android-bridge-helper.js"
    private var cachedScript: String? = null

    fun inject(webView: WebView, context: Context) {
        try {
            val script = cachedScript ?: context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }.also {
                cachedScript = it
            }
            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            android.util.Log.e("BridgeInjector", "Failed to inject bridge script", e)
        }
    }
}
