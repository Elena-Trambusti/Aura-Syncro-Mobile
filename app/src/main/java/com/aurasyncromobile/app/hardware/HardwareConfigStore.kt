package com.aurasyncromobile.app.hardware

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HardwareConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): JSONObject {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return defaultConfig()
        return runCatching { JSONObject(raw) }.getOrDefault(defaultConfig())
    }

    fun save(config: JSONObject) {
        prefs.edit().putString(KEY_CONFIG, config.toString()).apply()
    }

    fun getPrinter(id: String): JSONObject? {
        val printers = get().optJSONArray("printers") ?: return null
        for (index in 0 until printers.length()) {
            val printer = printers.optJSONObject(index) ?: continue
            if (printer.optString("id") == id) return printer
        }
        return null
    }

    fun getDefaultPrinter(): JSONObject? {
        val config = get()
        val defaultId = config.optString("defaultPrinterId", "")
        if (defaultId.isBlank()) return null
        return getPrinter(defaultId)
    }

    fun getPosConfig(): JSONObject? {
        val pos = get().opt("pos")
        if (pos == null || pos == JSONObject.NULL) return null
        return pos as? JSONObject
    }

    companion object {
        private const val PREFS_NAME = "aura_hardware_config"
        private const val KEY_CONFIG = "config"

        fun defaultConfig(): JSONObject =
            JSONObject()
                .put("printers", JSONArray())
                .put("pos", JSONObject.NULL)
                .put("defaultPrinterId", JSONObject.NULL)
    }
}
