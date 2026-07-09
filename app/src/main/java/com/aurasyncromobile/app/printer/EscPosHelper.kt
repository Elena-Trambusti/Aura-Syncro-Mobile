package com.aurasyncromobile.app.printer

import java.nio.charset.Charset

object EscPosHelper {
    private val CHARSET: Charset = Charset.forName("ISO-8859-1")

    fun initialize(): ByteArray = byteArrayOf(0x1B, 0x40)

    fun text(value: String): ByteArray = value.toByteArray(CHARSET)

    fun lineFeed(lines: Int = 1): ByteArray =
        ByteArray(lines) { 0x0A }

    fun alignCenter(): ByteArray = byteArrayOf(0x1B, 0x61, 0x01)

    fun alignLeft(): ByteArray = byteArrayOf(0x1B, 0x61, 0x00)

    fun boldOn(): ByteArray = byteArrayOf(0x1B, 0x45, 0x01)

    fun boldOff(): ByteArray = byteArrayOf(0x1B, 0x45, 0x00)

    fun cutPartial(): ByteArray = byteArrayOf(0x1D, 0x56, 0x01)

    fun buildReceipt(title: String, body: String): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        chunks += initialize()
        chunks += alignCenter()
        chunks += boldOn()
        chunks += text(title)
        chunks += lineFeed()
        chunks += boldOff()
        chunks += alignLeft()
        chunks += text(body)
        chunks += lineFeed(3)
        chunks += cutPartial()
        return chunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
    }

    fun concat(vararg parts: ByteArray): ByteArray =
        parts.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
}
