package com.aurasyncromobile.app

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView

class AuraSyncroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("aurasyncro")
        }
        
        // La pulizia dello storage deve avvenire sul thread principale (UI thread)
        Handler(Looper.getMainLooper()).post {
            try {
                com.aurasyncromobile.app.webview.AuraWebViewCompat.clearWebStorageOnAppUpdate(this)
            } catch (e: Exception) {
                android.util.Log.e("AuraApp", "Failed to clear web storage", e)
            }
        }
    }
}
