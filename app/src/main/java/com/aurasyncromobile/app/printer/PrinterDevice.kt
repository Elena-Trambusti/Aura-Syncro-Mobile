package com.aurasyncromobile.app.printer

import org.json.JSONObject

enum class PrinterType(val wireValue: String) {
    BLUETOOTH("bluetooth"),
    WIFI("wifi");

    companion object {
        fun fromWire(value: String): PrinterType? =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
    }
}

data class PrinterDevice(
    val id: String,
    val name: String,
    val type: PrinterType,
    val address: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type.wireValue)
        .put("address", address)
}
