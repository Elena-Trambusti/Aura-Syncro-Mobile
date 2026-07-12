package com.aurasyncromobile.app.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PrinterManager(private val context: Context) {
    @Volatile
    private var activeConnection: PrinterConnection? = null
    private val printLock = Any()

    fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.BLUETOOTH
            permissions += Manifest.permission.BLUETOOTH_ADMIN
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return permissions.toTypedArray()
    }

    fun hasRequiredPermissions(): Boolean =
        requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    fun isBluetoothEnabled(): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return adapter?.isEnabled == true
    }

    fun scanPrinters(includeDiscovery: Boolean = true): List<PrinterDevice> {
        val devices = linkedMapOf<String, PrinterDevice>()
        devices.putAll(scanBluetoothDevices(includeDiscovery))
        return devices.values.toList()
    }

    fun connect(type: PrinterType, address: String, name: String = address): PrinterDevice {
        disconnect()
        val device = PrinterDevice(
            id = "${type.wireValue}:$address",
            name = name,
            type = type,
            address = address,
        )
        val connection = when (type) {
            PrinterType.BLUETOOTH -> BluetoothPrinterConnection(context, device)
            PrinterType.WIFI -> NetworkPrinterConnection(device)
        }
        connection.connect()
        activeConnection = connection
        return device
    }

    fun disconnect() {
        activeConnection?.disconnect()
        activeConnection = null
    }

    fun getConnectedPrinter(): PrinterDevice? = activeConnection?.device

    fun isConnected(): Boolean = activeConnection?.isConnected() == true

    fun printRaw(data: ByteArray) {
        synchronized(printLock) {
            val connection = activeConnection ?: error("Nessuna stampante connessa")
            if (!connection.isConnected()) {
                connection.connect()
            }
            connection.write(data)
        }
    }

    fun printText(text: String) {
        val payload = EscPosHelper.concat(
            EscPosHelper.initialize(),
            EscPosHelper.text(text),
            EscPosHelper.lineFeed(3),
            EscPosHelper.cutPartial(),
        )
        printRaw(payload)
    }

    @SuppressLint("MissingPermission")
    private fun scanBluetoothDevices(includeDiscovery: Boolean): Map<String, PrinterDevice> {
        if (!hasRequiredPermissions()) return emptyMap()

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyMap()
        if (!adapter.isEnabled) return emptyMap()

        val devices = linkedMapOf<String, PrinterDevice>()

        adapter.bondedDevices.orEmpty().forEach { device ->
            devices[device.address] = device.toPrinterDevice()
        }

        if (!includeDiscovery) return devices

        val receiverRegistered = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = getBluetoothDevice(intent) ?: return
                        devices[device.address] = device.toPrinterDevice()
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> latch.countDown()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered.set(true)

            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
            latch.await(4, TimeUnit.SECONDS)
            adapter.cancelDiscovery()
        } catch (_: SecurityException) {
            // Permessi non concessi: restituiamo solo i dispositivi già associati.
        } finally {
            if (receiverRegistered.get()) {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }

        return devices
    }

    @Suppress("DEPRECATION")
    private fun getBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toPrinterDevice(): PrinterDevice =
        PrinterDevice(
            id = "bluetooth:$address",
            name = name?.ifBlank { address } ?: address,
            type = PrinterType.BLUETOOTH,
            address = address,
        )
}
