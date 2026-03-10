package com.hypex.hyflash.model

import android.hardware.usb.UsbDevice

sealed class DeviceState {
    object Disconnected : DeviceState()
    data class ADB(val device: UsbDevice, val serial: String) : DeviceState()
    data class Fastboot(val device: UsbDevice, val serial: String) : DeviceState()
    data class UsbPermissionRequired(val device: UsbDevice) : DeviceState()
}
