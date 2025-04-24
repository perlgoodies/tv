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
            Log.d(TAG, "Sending command: ${command.joinToString(", ") { "0x" + it.toInt().and(0xFF).toString(16) }}")

            val result = usbConnection!!.bulkTransfer(
                usbEndpoint,
                command,
                command.size,
                TIMEOUT
            )

            Log.d(TAG, "Command result: $result")
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
        // Complete TCL power toggle code - send as a single 32-bit value
        val tclCode = byteArrayOf(0x57, 0xE3.toByte(), 0x18, 0xE7.toByte())

        Log.d(TAG, "Sending TCL power code: 0x57E318E7")

        // Create a properly formatted command with the correct NEC protocol headers
        val formattedCommand = createNECCommand(tclCode)

        return sendCommand(formattedCommand)
    }

    // Create a properly formatted NEC command with correct headers and timing
    private fun createNECCommand(code: ByteArray): ByteArray {
        // Create a command with NEC protocol markers and proper timing
        val command = ByteArray(10 + code.size)

        // Header with proper NEC protocol information
        command[0] = 0x01  // NEC protocol marker
        command[1] = 0x00  // Frequency MSB (38kHz = 38)
        command[2] = 0x26  // Frequency LSB
        command[3] = 0x00  // Repeat count (0 = no repeat)
        command[4] = 0x01  // Command format (1 = standard)

        // Include the complete 32-bit code with proper NEC format
        System.arraycopy(code, 0, command, 5, code.size)

        // Add trailing byte to indicate it's a 32-bit code
        command[9] = 0x20  // 32 bits

        return command
    }

    /**
     * Debug function for testing multiple codes with different formats
     */
    suspend fun debugTclPowerCodes(): Boolean {
        // Common formats of IR signals for TCL TVs
        val formats = listOf(
            Triple("NEC standard", 38, byteArrayOf(0x57, 0xE3.toByte(), 0x18, 0xE7.toByte())),
            Triple("NEC variant 1", 36, byteArrayOf(0x57, 0xE3.toByte(), 0x18, 0xE7.toByte())),
            Triple("NEC variant 2", 40, byteArrayOf(0x57, 0xE3.toByte(), 0x18, 0xE7.toByte())),
            Triple("TCL specific", 38, byteArrayOf(0x57, 0xE3.toByte(), 0x04, 0xFB.toByte())),
            Triple("Roku TV format", 38, byteArrayOf(0xEA.toByte(), 0xC7.toByte(), 0x10, 0xEF.toByte())),
            Triple("Common toggle", 38, byteArrayOf(0x20, 0xDF.toByte(), 0x10, 0xEF.toByte()))
        )

        for ((name, frequency, code) in formats) {
            Log.d(TAG, "Trying $name at ${frequency}kHz")

            // Create format with custom timing
            val formattedCode = formatIrSignal(code, frequency)

            // Send the code
            val success = sendCommand(formattedCode)

            if (success) {
                Log.d(TAG, "Success with $name")
                return true
            }

            delay(1000)
        }

        return false
    }

    /**
     * Create a formatted IR code with all necessary timing information
     */
    private fun formatIrSignal(code: ByteArray, frequency: Int): ByteArray {
        // Standard NEC format has:
        // - 9ms leading pulse, 4.5ms leading space
        // - 560µs pulse for bit 0, 560µs space
        // - 560µs pulse for bit 1, 1690µs space

        // Header with protocol and frequency
        val header = byteArrayOf(
            0x01, // NEC protocol
            (frequency shr 8).toByte(),
            frequency.toByte(),
            0x00,
            code.size.toByte() // Length of the code in bytes
        )

        return header + code
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

    suspend fun sendCommandWithTiming(command: ByteArray, frequency: Int, pulse: Int, space: Int): Boolean {
        // Format command with specific timing
        val formattedCommand = formatCommandWithTiming(command, frequency, pulse, space)
        return sendCommand(formattedCommand)
    }

    /**
     * Format a command with specific timing parameters
     */
    private fun formatCommandWithTiming(command: ByteArray, frequency: Int, pulse: Int, space: Int): ByteArray {
        // Custom format with explicit timing
        val result = ByteArray(command.size + 6)

        // Header with timing information
        result[0] = 0xA0.toByte() // Custom header
        result[1] = frequency.toByte()
        result[2] = (pulse shr 8).toByte()
        result[3] = (pulse and 0xFF).toByte()
        result[4] = (space shr 8).toByte()
        result[5] = (space and 0xFF).toByte()

        // Copy the command data
        System.arraycopy(command, 0, result, 6, command.size)

        return result
    }

    suspend fun send55S405TkaaPowerCode(): Boolean {
        // Standard TCL power code
        val tclCode = byteArrayOf(0x57, 0xE3.toByte(), 0x18, 0xE7.toByte())

        Log.d(TAG, "Sending TCL 55S405TKAA power code: 0x57E318E7")

        // Try multiple formats to increase chances of success
        try {
            // Format 1: Standard NEC format
            val format1 = byteArrayOf(
                0x01, // NEC protocol
                0x00, 0x26, // 38kHz frequency (38000Hz >> 8, 38000Hz & 0xFF)
                0x00, 0x04, // 4 bytes length
                0x57, 0xE3.toByte(), 0x18, 0xE7.toByte() // The code itself
            )

            // Send the first format
            val result1 = sendCommand(format1)
            if (result1) return true

            delay(500)

            // Format 2: Raw format with timing data
            val format2 = byteArrayOf(
                0xA1.toByte(), // Raw format marker
                0x26, // 38kHz in 0.1kHz units
                0x00, 0x02, // 2 repeats
                0x57, 0xE3.toByte(), 0x18, 0xE7.toByte() // The code itself
            )

            // Send the second format
            val result2 = sendCommand(format2)
            if (result2) return true

            delay(500)

            // Format 3: Just the hex code itself as a last resort
            return sendCommand(tclCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending 55S405TKAA code: ${e.message}")
            return false
        }
    }
}