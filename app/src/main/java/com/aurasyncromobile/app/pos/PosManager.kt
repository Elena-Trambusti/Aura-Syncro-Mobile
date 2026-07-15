package com.aurasyncromobile.app.pos

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class PosManager(private val context: Context) {
    @Volatile
    private var pendingPayment: PaymentSession? = null

    private val knownPosPackages = listOf(
        "com.sumup.merchant" to "SumUp",
        "com.izettle.android" to "Zettle",
        "com.stripe.terminal" to "Stripe Terminal",
        "com.paypal.here" to "PayPal Here",
        "com.nexi.softpos" to "Nexi SoftPOS",
        "com.mypos" to "myPOS",
        "com.squareup" to "Square",
        "com.vivawallet.spoc.payapp" to "Viva Wallet",
        "eu.nets.pos" to "Nets",
        "com.pax.poslink" to "PAX",
    )

    fun listInstalledApps(): JSONArray {
        val packageManager = context.packageManager
        val installed = JSONArray()
        val seen = linkedSetOf<String>()

        knownPosPackages.forEach { (packageName, label) ->
            if (isPackageInstalled(packageName)) {
                installed.put(appEntry(packageManager, packageName, label))
                seen += packageName
            }
        }

        return installed
    }

    fun listLaunchableApps(query: String = ""): JSONArray {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val apps = JSONArray()
        val seen = linkedSetOf<String>()

        activities
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (!seen.add(packageName)) return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager)?.toString()
                    ?: packageName
                if (normalizedQuery.isNotBlank() &&
                    !label.lowercase(Locale.ROOT).contains(normalizedQuery) &&
                    !packageName.lowercase(Locale.ROOT).contains(normalizedQuery)
                ) {
                    return@mapNotNull null
                }
                appEntry(packageManager, packageName, label)
            }
            .sortedBy { it.optString("label").lowercase(Locale.ROOT) }
            .forEach { apps.put(it) }

        return apps
    }

    fun probeApp(packageName: String): JSONObject {
        val packageManager = context.packageManager
        val installed = isPackageInstalled(packageName)
        val payload = JSONObject()
            .put("packageName", packageName)
            .put("installed", installed)

        if (!installed) return payload.put("launchable", false)

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val label = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)

        return payload
            .put("label", label)
            .put("launchable", launchIntent != null)
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

    fun startPayment(
        packageName: String,
        amountCents: Long,
        orderId: String,
        currency: String = "EUR",
        deepLinkTemplate: String? = null,
    ): PaymentSession {
        val session = PaymentSession(
            orderId = orderId,
            amountCents = amountCents,
            currency = currency,
            packageName = packageName,
        )
        pendingPayment = session

        val deepLink = resolveDeepLink(
            template = deepLinkTemplate,
            amountCents = amountCents,
            orderId = orderId,
            currency = currency,
        )
        val opened = openExternalApp(packageName, deepLink)
        if (!opened) {
            pendingPayment = null
            error("App POS non trovata o non avviabile: $packageName")
        }
        return session
    }

    fun resolveDeepLink(
        template: String?,
        amountCents: Long,
        orderId: String,
        currency: String,
    ): String? {
        if (template.isNullOrBlank()) return null

        val callback = buildCallbackUrl(orderId)
        val amountDecimal = String.format(Locale.US, "%.2f", amountCents / 100.0)

        return template
            .replace("{{amount}}", amountCents.toString())
            .replace("{{amount_decimal}}", amountDecimal)
            .replace("{{currency}}", currency)
            .replace("{{orderId}}", orderId)
            .replace("{{callback}}", urlEncode(callback))
            .replace("{{callback_url}}", urlEncode(callback))
    }

    fun buildCallbackUrl(orderId: String): String =
        "aurasyncro://payment-result?orderId=${urlEncode(orderId)}"

    fun getPendingPayment(): PaymentSession? = pendingPayment

    fun completePayment(
        orderId: String,
        status: String,
        txId: String? = null,
        source: String = "manual",
    ): JSONObject {
        val session = pendingPayment
        if (session != null && session.orderId != orderId) {
            error("Ordine in attesa diverso: ${session.orderId}")
        }
        pendingPayment = null
        return JSONObject()
            .put("status", status)
            .put("orderId", orderId)
            .put("txId", txId ?: JSONObject.NULL)
            .put("source", source)
            .put("amountCents", session?.amountCents ?: JSONObject.NULL)
            .put("currency", session?.currency ?: JSONObject.NULL)
            .put("packageName", session?.packageName ?: JSONObject.NULL)
    }

    fun cancelPendingPayment(): Boolean {
        val hadPending = pendingPayment != null
        pendingPayment = null
        return hadPending
    }

    fun parsePaymentResultUri(uri: Uri): JSONObject? {
        if (uri.scheme != "aurasyncro" || uri.host != "payment-result") return null
        return JSONObject()
            .put("status", uri.getQueryParameter("status") ?: "unknown")
            .put("orderId", uri.getQueryParameter("orderId") ?: "")
            .put("txId", uri.getQueryParameter("txId") ?: JSONObject.NULL)
            .put("source", "deep-link")
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

    private fun isPackageInstalled(packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)

    private fun appEntry(
        packageManager: PackageManager,
        packageName: String,
        label: String,
    ): JSONObject {
        val launchable = packageManager.getLaunchIntentForPackage(packageName) != null
        return JSONObject()
            .put("packageName", packageName)
            .put("label", label)
            .put("launchable", launchable)
            .put("knownPos", knownPosPackages.any { it.first == packageName })
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
