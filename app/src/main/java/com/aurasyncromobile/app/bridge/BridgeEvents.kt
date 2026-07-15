package com.aurasyncromobile.app.bridge

import android.webkit.WebView
import org.json.JSONObject

object BridgeEvents {
    fun dispatch(webView: WebView, eventName: String, detail: JSONObject = JSONObject()) {
        val payload = detail.toString()
        val script =
            "window.dispatchEvent(new CustomEvent('$eventName',{detail:$payload}))"
        webView.post { webView.evaluateJavascript(script, null) }
    }
}
