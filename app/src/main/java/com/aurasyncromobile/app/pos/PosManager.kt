package com.aurasyncromobile.app.pos

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

class PosManager(private val context: Context) {
    private val knownPosPackages = listOf(
        "com.sumup.merchant" to "SumUp",
        "com.izettle.android" to "Zettle",
        "com.stripe.terminal" to "Stripe Terminal",
        "com.paypal.here" to "PayPal Here",
        "com.nexi.softpos" to "Nexi SoftPOS",
        "com.mypos" to "myPOS",
        "com.squareup" to "Square",
    )

    fun listInstalledApps(): JSONArray {
        val packageManager = context.packageManager
        val installed = JSONArray()
        knownPosPackages.forEach { (packageName, label) ->
            val isInstalled = runCatching {
                packageManager.getPackageInfo(packageName, 0)
                true
            }.getOrDefault(false)
            if (isInstalled) {
                installed.put(
                    JSONObject()
                        .put("packageName", packageName)
                        .put("label", label),
                )
            }
        }
        return installed
    }

    fun openExternalApp(packageName: String, deepLink: String? = null): Boolean {
        val packageManager = context.packageManager
        val launchIntent = if (!deepLink.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                setPackage(packageName)
            }
        } else {
            packageManager.getLaunchIntentForPackage(packageName)
        } ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
