package com.aurasyncromobile.app.pos

import org.json.JSONObject

data class PaymentSession(
    val orderId: String,
    val amountCents: Long,
    val currency: String,
    val packageName: String,
    val startedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("orderId", orderId)
            .put("amountCents", amountCents)
            .put("currency", currency)
            .put("packageName", packageName)
            .put("startedAt", startedAt)
            .put("pending", true)
}
