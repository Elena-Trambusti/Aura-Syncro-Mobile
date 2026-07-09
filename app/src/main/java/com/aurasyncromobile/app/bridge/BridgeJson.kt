package com.aurasyncromobile.app.bridge

import org.json.JSONArray
import org.json.JSONObject

object BridgeJson {
    fun success(payload: JSONObject = JSONObject()): String =
        JSONObject()
            .put("ok", true)
            .put("data", payload)
            .toString()

    fun successArray(items: JSONArray): String =
        JSONObject()
            .put("ok", true)
            .put("data", items)
            .toString()

    fun error(message: String): String =
        JSONObject()
            .put("ok", false)
            .put("error", message)
            .toString()
}
