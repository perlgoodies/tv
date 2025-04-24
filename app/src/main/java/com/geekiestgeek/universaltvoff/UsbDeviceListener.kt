package com.geekiestgeek.universaltvoff

interface UsbDeviceListener {
    fun onUsbDeviceDetected()
    fun onUsbDeviceConnected()
    fun onUsbDeviceConnectionFailed()
    fun onUsbDeviceDetectionFailed()
    fun onUsbPermissionDenied()
    fun onUsbDeviceDisconnected()
}