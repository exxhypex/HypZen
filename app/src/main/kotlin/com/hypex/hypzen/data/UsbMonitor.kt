package com.hypex.hypzen.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.hypex.hypzen.model.DeviceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbMonitor(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val ACTION_USB_PERMISSION = "com.hypex.hypzen.USB_PERMISSION"

    private val _deviceState = MutableStateFlow<DeviceState>(DeviceState.Disconnected)
    val deviceState = _deviceState.asStateFlow()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                ACTION_USB_PERMISSION -> {
                    updateDeviceStatus()
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        updateDeviceStatus()
    }

    fun stop() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {}
    }

    private fun updateDeviceStatus() {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            _deviceState.value = DeviceState.Disconnected
            return
        }

        for (device in deviceList.values) {
            if (!usbManager.hasPermission(device)) {
                requestPermission(device)
                _deviceState.value = DeviceState.UsbPermissionRequired(device)
                return
            }

            val adbInterface = findInterface(device, 0xff, 0x42, 0x01)
            if (adbInterface != null) {
                _deviceState.value = DeviceState.ADB(device, device.serialNumber ?: "Unknown")
                return
            }

            val fastbootInterface = findInterface(device, 0xff, 0x42, 0x03)
            if (fastbootInterface != null) {
                _deviceState.value = DeviceState.Fastboot(device, device.serialNumber ?: "Unknown")
                return
            }
        }
        _deviceState.value = DeviceState.Disconnected
    }

    private fun findInterface(device: UsbDevice, cls: Int, subCls: Int, protocol: Int): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val itf = device.getInterface(i)
            if (itf.interfaceClass == cls && itf.interfaceSubclass == subCls && itf.interfaceProtocol == protocol) {
                return itf
            }
        }
        return null
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName) // Make intent explicit
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, pendingIntent)
    }
}
