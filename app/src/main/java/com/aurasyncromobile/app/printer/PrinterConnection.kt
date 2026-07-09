package com.aurasyncromobile.app.printer

interface PrinterConnection {
    val device: PrinterDevice
    fun connect()
    fun write(data: ByteArray)
    fun disconnect()
    fun isConnected(): Boolean
}
