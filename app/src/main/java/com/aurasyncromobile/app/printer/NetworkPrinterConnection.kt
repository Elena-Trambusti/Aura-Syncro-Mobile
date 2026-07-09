package com.aurasyncromobile.app.printer

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class NetworkPrinterConnection(
    override val device: PrinterDevice,
    private val connectTimeoutMs: Int = 5_000,
    private val writeTimeoutMs: Int = 10_000,
) : PrinterConnection {
    private var socket: Socket? = null
    private var output: OutputStream? = null

    override fun connect() {
        val (host, port) = parseHostPort(device.address)
        val activeSocket = Socket()
        activeSocket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        activeSocket.soTimeout = writeTimeoutMs
        socket = activeSocket
        output = activeSocket.getOutputStream()
    }

    override fun write(data: ByteArray) {
        val stream = output ?: error("Stampante di rete non connessa")
        stream.write(data)
        stream.flush()
    }

    override fun disconnect() {
        runCatching { output?.close() }
        runCatching { socket?.close() }
        output = null
        socket = null
    }

    override fun isConnected(): Boolean = socket?.isConnected == true

    companion object {
        const val DEFAULT_PORT = 9100

        fun parseHostPort(address: String): Pair<String, Int> {
            val trimmed = address.trim()
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":")
                val host = parts.dropLast(1).joinToString(":")
                val port = parts.last().toIntOrNull() ?: DEFAULT_PORT
                return host to port
            }
            return trimmed to DEFAULT_PORT
        }
    }
}
