package com.aurasyncromobile.app.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.OutputStream
import java.util.UUID

class BluetoothPrinterConnection(
    context: Context,
    override val device: PrinterDevice,
) : PrinterConnection {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    override fun connect() {
        val bluetoothAdapter = adapter ?: error("Bluetooth non disponibile su questo dispositivo")
        if (!bluetoothAdapter.isEnabled) error("Bluetooth disattivato")

        val bluetoothDevice: BluetoothDevice =
            bluetoothAdapter.getRemoteDevice(device.address)

        val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        socket = bluetoothDevice.createRfcommSocketToServiceRecord(sppUuid).also { activeSocket ->
            bluetoothAdapter.cancelDiscovery()
            activeSocket.connect()
            output = activeSocket.outputStream
        }
    }

    override fun write(data: ByteArray) {
        val stream = output ?: error("Stampante Bluetooth non connessa")
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
}
