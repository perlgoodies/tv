package com.geekiestgeek.universaltvoff.ir

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Handles the transmission of IR codes
 */
class CodeTransmitter(
    private val context: Context,
    private val irBlasterManager: IrBlasterManager
) {
    private val TAG = "CodeTransmitter"

    // Transmission state
    private var isRunning = false

    /**
     * Send a single IR code
     */
    suspend fun sendCode(code: CodeDatabase.IRCode): Boolean {
        Log.d(TAG, "Sending code for ${code.manufacturer} ${code.model}, frequency: ${code.frequency}kHz")

        // Check for special handlers first (for specific models)
        val specialKey = "${code.manufacturer} ${code.model}".trim()
        val specialHandler = CodeDatabase.specialHandlers[specialKey]

        if (specialHandler != null) {
            Log.d(TAG, "Using special handler for $specialKey")
            val commandBytes = specialHandler(code)
            return irBlasterManager.sendCommand(commandBytes)
        }

        // For TCL TVs, try the manufacturer-specific handling
        if (code.manufacturer == "TCL") {
            // Try direct TCL method first for TCL TVs
            val tclSuccess = irBlasterManager.sendTclPowerCode()
            if (tclSuccess) {
                Log.d(TAG, "Successfully sent TCL power code using direct method")
                return true
            } else {
                Log.d(TAG, "Direct TCL method failed, falling back to generic approach")
            }
        }

        // Convert the string pattern to timing values
        val timings = CodeDatabase.convertPatternToTimings(code.pattern)

        // Convert the generic code to your IR blaster's format
        val commandBytes = convertToIrBlasterFormat(code.frequency, timings, code)

        // Send the command to the IR blaster
        val success = irBlasterManager.sendCommand(commandBytes)

        if (success) {
            Log.d(TAG, "Successfully sent IR command")
        } else {
            Log.e(TAG, "Failed to send IR command")
        }

        return success
    }

    /**
     * Convert IR timing pattern to the format expected by different IR blaster devices
     */
    private fun convertToIrBlasterFormat(
        frequency: Int,
        timings: List<Int>,
        code: CodeDatabase.IRCode
    ): ByteArray {
        // This implementation supports multiple IR blaster protocols
        // We'll create several formats and concatenate them to increase compatibility

        // Format 1: Standard NEC protocol format (common in many IR blasters)
        val format1 = createStandardNecFormat(frequency, timings)

        // Format 2: Raw timing format (used by some IR blasters)
        val format2 = createRawTimingFormat(frequency, timings)

        // Format 3: Simplified Command format (for basic IR blasters)
        val format3 = createSimplifiedFormat(code)

        // Combine all formats into a single command - the IR blaster will parse the format it recognizes
        return format1 + format2 + format3
    }

    /**
     * Creates a standard NEC protocol format command
     */
    private fun createStandardNecFormat(frequency: Int, timings: List<Int>): ByteArray {
        // Standard format used by many IR blasters:
        // Byte 0: Protocol (1 = NEC)
        // Byte 1-2: Frequency (16-bit value in kHz)
        // Byte 3-4: Pattern length (16-bit value, number of timing entries)
        // Remaining bytes: Timing values (each as 16-bit value in µs)

        val command = ByteArray(5 + (timings.size * 2))

        // Protocol code (1 = NEC)
        command[0] = 0x01

        // Frequency (16-bit value, MSB first)
        command[1] = (frequency shr 8).toByte()
        command[2] = (frequency and 0xFF).toByte()

        // Pattern length (16-bit value, MSB first)
        command[3] = (timings.size shr 8).toByte()
        command[4] = (timings.size and 0xFF).toByte()

        // Timing values (each as 16-bit value, MSB first)
        for (i in timings.indices) {
            val timing = timings[i]
            val byteIndex = 5 + (i * 2)
            command[byteIndex] = (timing shr 8).toByte()
            command[byteIndex + 1] = (timing and 0xFF).toByte()
        }

        return command
    }

    /**
     * Creates a raw timing format command
     */
    private fun createRawTimingFormat(frequency: Int, timings: List<Int>): ByteArray {
        // Raw format used by some IR blasters:
        // Byte 0: 0xA1 (marker for raw format)
        // Byte 1: Frequency (in 0.1kHz, 38kHz = 380)
        // Byte 2-3: Repeat count (typically 1)
        // Byte 4-5: Number of pairs (times 2 for pairs of mark/space)
        // Remaining bytes: Timing values (pairs of mark/space, each 16-bit)

        val pairCount = timings.size / 2
        val command = ByteArray(6 + (timings.size * 2))

        command[0] = 0xA1.toByte()
        command[1] = ((frequency * 10) and 0xFF).toByte() // Convert kHz to 0.1kHz units

        // Repeat count (16-bit value, MSB first) - typically 1
        command[2] = 0x00
        command[3] = 0x01

        // Number of pairs (16-bit value, MSB first)
        command[4] = (pairCount shr 8).toByte()
        command[5] = (pairCount and 0xFF).toByte()

        // Timing values (each as 16-bit value, MSB first)
        for (i in timings.indices) {
            val timing = timings[i]
            val byteIndex = 6 + (i * 2)
            command[byteIndex] = (timing shr 8).toByte()
            command[byteIndex + 1] = (timing and 0xFF).toByte()
        }

        return command
    }

    /**
     * Creates a simplified command format based on protocol type
     */
    private fun createSimplifiedFormat(code: CodeDatabase.IRCode): ByteArray {
        // Convert hex code string to bytes
        val hexCode = code.hexCode.replace("0x", "")
        val bytes = mutableListOf<Byte>()

        if (hexCode.isNotEmpty()) {
            try {
                // Add protocol marker
                when (code.protocol) {
                    CodeDatabase.Protocol.NEC -> bytes.add(0xB1.toByte())
                    CodeDatabase.Protocol.SONY -> bytes.add(0xB2.toByte())
                    CodeDatabase.Protocol.RC5 -> bytes.add(0xB3.toByte())
                    CodeDatabase.Protocol.RC6 -> bytes.add(0xB4.toByte())
                    CodeDatabase.Protocol.SAMSUNG -> bytes.add(0xB5.toByte())
                    CodeDatabase.Protocol.PANASONIC -> bytes.add(0xB6.toByte())
                    CodeDatabase.Protocol.JVC -> bytes.add(0xB7.toByte())
                    else -> bytes.add(0xB0.toByte())
                }

                // Add command bytes from hex code
                for (i in 0 until hexCode.length step 2) {
                    if (i + 1 < hexCode.length) {
                        val byteStr = hexCode.substring(i, i + 2)
                        bytes.add(byteStr.toInt(16).toByte())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing hex code: ${code.hexCode}", e)
            }
        }

        // For empty or invalid hex codes, add a default value
        if (bytes.isEmpty()) {
            bytes.add(0xB0.toByte()) // Default protocol marker
            bytes.add(0x00) // Default command
        }

        return bytes.toByteArray()
    }

    /**
     * Start sending power codes for selected device types and manufacturers
     */
    fun startSendingCodes(
        deviceTypes: List<CodeDatabase.DeviceType> = listOf(CodeDatabase.DeviceType.TV),
        selectedManufacturers: List<String> = emptyList(),
        useQuickMode: Boolean = false,
        powerAction: CodeDatabase.PowerAction = CodeDatabase.PowerAction.TOGGLE
    ): Flow<TransmissionProgress> = flow {
        if (isRunning) {
            Log.d(TAG, "Already running, ignoring start request")
            return@flow
        }

        isRunning = true
        var totalProcessed = 0
        var totalCodes = 0

        try {
            // Process each device type
            for (deviceType in deviceTypes) {
                val codes = when {
                    // If specific manufacturers are selected
                    selectedManufacturers.isNotEmpty() -> {
                        val allCodes = mutableListOf<CodeDatabase.IRCode>()
                        for (manufacturer in selectedManufacturers) {
                            val manufacturerCodes = CodeDatabase.getCodesForManufacturer(
                                context, deviceType, manufacturer
                            )
                            allCodes.addAll(manufacturerCodes)
                        }
                        allCodes
                    }
                    // If quick mode is enabled for TVs, use the priority order
                    deviceType == CodeDatabase.DeviceType.TV && useQuickMode -> {
                        CodeDatabase.getTvCodesInQuickModeOrder(context)
                    }
                    // Otherwise, get all codes for this device type
                    else -> {
                        CodeDatabase.getAllCodesForDeviceType(context, deviceType)
                    }
                }

                // Filter by power action if specified (or use TOGGLE as fallback)
                val filteredCodes = codes.filter {
                    it.powerAction == powerAction ||
                            (it.powerAction == CodeDatabase.PowerAction.TOGGLE &&
                                    codes.none { code -> code.manufacturer == it.manufacturer && code.powerAction == powerAction })
                }

                totalCodes += filteredCodes.size
                emit(TransmissionProgress(totalProcessed, totalCodes, "", deviceType, completed = false))

                // Send each code with a delay between
                for ((index, code) in filteredCodes.withIndex()) {
                    // Stop if requested
                    if (!isRunning) {
                        emit(TransmissionProgress(
                            totalProcessed, totalCodes, code.manufacturer,
                            deviceType, completed = false
                        ))
                        Log.d(TAG, "Transmission stopped by user request")
                        break
                    }

                    // Emit progress before sending
                    emit(TransmissionProgress(
                        totalProcessed, totalCodes, code.manufacturer,
                        deviceType, completed = false
                    ))

                    Log.d(TAG, "Sending code ${index+1}/${filteredCodes.size} for ${code.manufacturer}")

                    try {
                        // Send the code
                        val success = sendCode(code)

                        if (!success) {
                            Log.e(TAG, "Failed to send code for ${code.manufacturer}")
                        } else {
                            Log.d(TAG, "Successfully sent code for ${code.manufacturer}")
                        }

                        // Add a delay between codes
                        delay(CODE_TRANSMISSION_DELAY_MS)
                        totalProcessed++

                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending code: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transmission: ${e.message}")
        } finally {
            isRunning = false
            emit(TransmissionProgress(totalProcessed, totalCodes, "", null, completed = true))
            Log.d(TAG, "Transmission sequence completed")
        }
    }

    /**
     * Stop the transmission sequence
     */
    fun stopTransmission() {
        isRunning = false
        Log.d(TAG, "Stopping transmission sequence")
    }

    /**
     * Data class for transmission progress updates
     */
    data class TransmissionProgress(
        val current: Int,
        val total: Int,
        val currentManufacturer: String,
        val currentDeviceType: CodeDatabase.DeviceType? = null,
        val completed: Boolean
    ) {
        // Calculate progress percentage
        val progressPercent: Int = if (total > 0) (current * 100 / total) else 0
    }

    companion object {
        // Delay between sending codes (ms)
        const val CODE_TRANSMISSION_DELAY_MS = 500L
    }
}