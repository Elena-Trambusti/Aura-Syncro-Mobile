package com.aurasyncromobile.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import com.aurasyncromobile.app.bridge.BridgeJson
import com.aurasyncromobile.app.pos.PosManager
import com.aurasyncromobile.app.printer.PrinterManager
import com.aurasyncromobile.app.printer.PrinterType
import org.json.JSONArray
import org.json.JSONObject

class WebAppInterface(
    private val context: Context,
    private val printerManager: PrinterManager,
    private val posManager: PosManager,
    private val onRequestPermissions: () -> Unit,
    private val onCompatEvent: (String, String) -> Unit = { _, _ -> },
) {

    @JavascriptInterface
    fun onAuraCompatEvent(event: String, detailJson: String): String {
        onCompatEvent(event, detailJson)
        return BridgeJson.success()
    }

    @JavascriptInterface
    fun getPlatform(): String = "android"

    @JavascriptInterface
    fun getAppVersion(): String =
        runCatching {
            val packageManager = context.packageManager
            val versionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0).versionName
            }
            versionName ?: "1.0"
        }.getOrDefault("1.0")

    @JavascriptInterface
    fun requestHardwarePermissions(): String {
        onRequestPermissions()
        return BridgeJson.success(
            JSONObject().put("requested", true),
        )
    }

    @JavascriptInterface
    fun hasHardwarePermissions(): String =
        BridgeJson.success(
            JSONObject().put("granted", printerManager.hasRequiredPermissions()),
        )

    @JavascriptInterface
    fun isBluetoothEnabled(): String =
        runCatching {
            BridgeJson.success(
                JSONObject().put("enabled", printerManager.isBluetoothEnabled()),
            )
        }.getOrElse { error ->
            BridgeJson.error(error.message ?: "Errore verifica Bluetooth")
        }

    @JavascriptInterface
    fun openBluetoothSettings(): String {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return BridgeJson.success()
    }

    @JavascriptInterface
    fun scanPrinters(includeDiscovery: Boolean = true): String =
        runCatching {
            if (!printerManager.hasRequiredPermissions()) {
                return BridgeJson.error("Permessi Bluetooth mancanti. Chiama requestHardwarePermissions().")
            }
            val printers = printerManager.scanPrinters(includeDiscovery)
            val array = JSONArray()
            printers.forEach { printer -> array.put(printer.toJson()) }
            BridgeJson.successArray(array)
        }.getOrElse { error ->
            BridgeJson.error(error.message ?: "Scansione stampanti fallita")
        }

    @JavascriptInterface
    fun connectPrinter(type: String, address: String, name: String = address): String =
        runCatching {
            val printerType = PrinterType.fromWire(type)
                ?: error("Tipo stampante non valido. Usa 'bluetooth' o 'wifi'.")
            val device = printerManager.connect(printerType, address, name)
            BridgeJson.success(device.toJson())
        }.getOrElse { error ->
            BridgeJson.error(error.message ?: "Connessione stampante fallita")
        }

    @JavascriptInterface
    fun disconnectPrinter(): String =
        runCatching {
            printerManager.disconnect()
            BridgeJson.success()
        }.getOrElse { error ->
            BridgeJson.error(error.message ?: "Disconnessione fallita")
        }

    @JavascriptInterface
    fun getPrinterStatus(): String {
        val connected = printerManager.getConnectedPrinter()
        val payload = JSONObject()
            .put("connected", printerManager.isConnected())
        if (connected != null) {
            payload.put("printer", connected.toJson())
        }
        return BridgeJson.success(payload)
    }

    @JavascriptInterface
    fun printEscPosBase64(base64Data: String): String =
        runCatching {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            printerManager.printRaw(bytes)
            BridgeJson.success(JSONObject().put("bytes", bytes.size))
        }.getOrElse { error ->
            BridgeJson.error(error.message ?: "Stampa fallita")
        }

    @JavascriptInterface
    fun printText(text: String): String =
        runCatching {
            printerManager.printText(text)
            BridgeJson.success()
        }.getOrElse { error ->
            BridgeJson.error(error.message ?: "Stampa testo fallita")
        }

    @JavascriptInterface
    fun listPosApps(): String =
        BridgeJson.successArray(posManager.listInstalledApps())

    @JavascriptInterface
    fun openPosApp(packageName: String, deepLink: String? = null): String =
        runCatching {
            val opened = posManager.openExternalApp(packageName, deepLink)
            if (!opened) error("App POS non trovata o non avviabile")
            BridgeJson.success(JSONObject().put("packageName", packageName))
        }.getOrElse { error ->
            BridgeJson.error(error.message ?: "Apertura app POS fallita")
        }

    @JavascriptInterface
    fun openExternalUrl(url: String): String =
        runCatching {
            val opened = posManager.openUrl(url)
            if (!opened) error("URL non apribile")
            BridgeJson.success(JSONObject().put("url", url))
        }.getOrElse { error ->
            BridgeJson.error(error.message ?: "Apertura URL fallita")
        }
}
