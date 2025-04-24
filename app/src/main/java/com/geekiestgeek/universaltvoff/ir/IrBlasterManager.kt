package com.geekiestgeek.universaltvoff.ir

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Manages communication with the USB IR blaster device
 */
class IrBlasterManager(private val context: Context) {

    private val TAG = "IrBlasterManager"

    // USB system services
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    // Connection objects
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint: UsbEndpoint? = null

    // Status
    private var isConnected = false

    /**
     * Checks if an IR blaster is connected to the device
     */
    fun hasConnectedBlaster(): Boolean {
        val deviceList = usbManager.deviceList

        if (deviceList.isEmpty()) {
            Log.d(TAG, "No USB devices found")
            return false
        }

        // Log all connected devices for debugging
        for (device in deviceList.values) {
            Log.d(TAG, "Found USB device: ${device.deviceName}, " +
                    "VendorID: ${device.vendorId.toString(16)}, ProductID: ${device.productId.toString(16)}")
        }

        // Try to find a supported IR blaster device
        for (device in deviceList.values) {
            // Check if the device has at least one interface
            if (device.interfaceCount > 0) {
                usbDevice = device
                Log.d(TAG, "Selected USB device: ${device.deviceName}")
                return true
            }
        }

        Log.d(TAG, "No suitable USB device found")
        return false
    }

    /**
     * Requests permission to communicate with the USB device
     */
    fun requestPermission(onPermissionGranted: () -> Unit) {
        val device = usbDevice ?: return

        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Permission already granted for device")
            onPermissionGranted()
            return
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * Opens a connection to the USB device
     */
    fun connect(): Boolean {
        val device = usbDevice ?: return false

        usbConnection = usbManager.openDevice(device)
        if (usbConnection == null) {
            Log.e(TAG, "Could not open USB connection")
            return false
        }

        // Find a suitable interface
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)

            // Try to find an endpoint for output
            for (j in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(j)

                // Prefer BULK OUT endpoint
                if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                    usbInterface = intf
                    usbEndpoint = endpoint
                    break
                }
            }

            if (usbEndpoint != null) break
        }

        if (usbInterface == null || usbEndpoint == null) {
            Log.e(TAG, "Could not find suitable interface or endpoint")
            disconnect()
            return false
        }

        // Claim the interface
        if (!usbConnection!!.claimInterface(usbInterface, true)) {
            Log.e(TAG, "Could not claim interface")
            disconnect()
            return false
        }

        isConnected = true
        Log.d(TAG, "Successfully connected to USB device")
        return true
    }

    /**
     * Disconnects from the USB device
     */
    fun disconnect() {
        try {
            usbConnection?.releaseInterface(usbInterface)
            usbConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        } finally {
            usbInterface = null
            usbConnection = null
            usbEndpoint = null
            isConnected = false
        }
    }

    /**
     * Sends raw commands to the device
     */
    suspend fun sendCommand(command: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected) {
            Log.e(TAG, "Not connected to USB device")
            return@withContext false
        }

        try {
            val result = usbConnection!!.bulkTransfer(
                usbEndpoint,
                command,
                command.size,
                TIMEOUT
            )

            return@withContext result >= 0
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: ${e.message}")
            return@withContext false
        }
    }

    /**
     * For TCL testing - send a TCL power off code
     */
    suspend fun sendTclPowerCode(): Boolean {
        // TCL 75Q750G power code
        val tclCode = byteArrayOf(0x57, 0xE3.toByte(), 0x18, 0xE7.toByte())

        Log.d(TAG, "Sending TCL power code: 0x57E318E7")
        return sendCommand(tclCode)
    }

    /**
     * Debug function for testing multiple codes
     */
    suspend fun debugTclPowerCodes(): Boolean {
        // Just test a few common codes
        val codes = listOf(
            byteArrayOf(0x57, 0xE3.toByte(), 0x18, 0xE7.toByte()),
            byteArrayOf(0x40, 0xBF.toByte(), 0x38, 0xC7.toByte()),
            byteArrayOf(0x10, 0xEF.toByte(), 0x38, 0xC7.toByte())
        )

        for (code in codes) {
            if (sendCommand(code)) return true
            delay(1000)
        }

        return false
    }

    /**
     * Alternative implementation with timing patterns
     */
    suspend fun send75Q750GPowerCode(): Boolean {
        // Just send the raw code again
        return sendTclPowerCode()
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.geekiestgeek.universaltvoff.USB_PERMISSION"
        const val TIMEOUT = 5000 // ms
    }
}