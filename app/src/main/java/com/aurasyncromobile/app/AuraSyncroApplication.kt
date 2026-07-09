package com.aurasyncromobile.app

import android.app.Application
import android.os.Build
import android.webkit.WebView

class AuraSyncroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("aurasyncro")
        }
        com.aurasyncromobile.app.webview.AuraWebViewCompat.clearWebStorageOnAppUpdate(this)
    }
}
