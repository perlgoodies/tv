package com.geekiestgeek.universaltvoff.ir

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced Database of IR codes for various devices
 * Simplified version for initial development
 */
object CodeDatabase {

    private const val TAG = "CodeDatabase"

    /**
     * Enum for device types
     */
    enum class DeviceType {
        TV,
        PROJECTOR,
        DVD_PLAYER,
        MONITOR,
        AUDIO_RECEIVER,
        OTHER
    }

    /**
     * Power action type (ON, OFF, or TOGGLE)
     */
    enum class PowerAction {
        ON,
        OFF,
        TOGGLE
    }

    /**
     * IR Protocol type
     */
    enum class Protocol {
        NEC,
        RC5,
        RC6,
        SONY,
        SAMSUNG,
        PANASONIC,
        JVC,
        UNKNOWN
    }

    /**
     * Data structure for IR code storage
     */
    data class IRCode(
        val manufacturer: String,
        val model: String = "",              // Optional specific model
        val deviceType: DeviceType,
        val protocol: Protocol = Protocol.NEC,
        val frequency: Int,                  // kHz
        val pattern: String,                 // Simplified representation of timing/signal pattern
        val powerAction: PowerAction,        // Power action type
        val hexCode: String = "",            // Optional direct hex code representation
        val bits: Int = 32                   // Bit length, default 32
    )

    // Quick mode priority
    val quickModePriority = listOf(
        "Samsung",
        "LG",
        "Sony",
        "TCL",
        "Vizio",
        "Hisense",
        "Insignia",
        "Philips",
        "Sharp",
        "Toshiba"
    )

    /**
     * Get the list of manufacturers for a specific device type
     */
    fun getManufacturersForDeviceType(deviceType: DeviceType): List<String> {
        return when (deviceType) {
            DeviceType.TV -> listOf(
                "TCL", "Samsung", "LG", "Sony", "Vizio", "Hisense",
                "Philips", "Sharp", "Toshiba", "Panasonic", "Insignia"
            )
            DeviceType.PROJECTOR -> listOf(
                "Epson", "BenQ", "Sony", "Optoma", "ViewSonic", "Acer", "Panasonic"
            )
            DeviceType.DVD_PLAYER -> listOf(
                "Sony", "Samsung", "LG", "Panasonic", "Philips"
            )
            DeviceType.MONITOR -> listOf(
                "Dell", "Samsung", "LG", "Acer", "HP", "ASUS", "ViewSonic"
            )
            DeviceType.AUDIO_RECEIVER -> listOf(
                "Yamaha", "Denon", "Sony", "Pioneer", "Onkyo", "Marantz"
            )
            DeviceType.OTHER -> listOf("Other")
        }
    }

    /**
     * Get codes for a specific device type and manufacturer
     * Simplified implementation for development
     */
    suspend fun getCodesForManufacturer(
        context: Context,
        deviceType: DeviceType,
        manufacturer: String
    ): List<IRCode> = withContext(Dispatchers.IO) {
        // For now, return some dummy codes for testing
        return@withContext when {
            manufacturer == "TCL" && deviceType == DeviceType.TV -> {
                listOf(
                    IRCode(
                        manufacturer = "TCL",
                        deviceType = DeviceType.TV,
                        protocol = Protocol.NEC,
                        frequency = 38,
                        pattern = "9000,4500,560,1690,560,1690,560,560,560,1690,560,1690,560,560,560,560",
                        powerAction = PowerAction.TOGGLE,
                        hexCode = "0x57E318E7"
                    ),
                    IRCode(
                        manufacturer = "TCL",
                        deviceType = DeviceType.TV,
                        protocol = Protocol.NEC,
                        frequency = 38,
                        pattern = "9000,4500,560,1690,560,1690,560,560,560,1690,560,1690,560,560,560,560",
                        powerAction = PowerAction.OFF,
                        hexCode = "0x57E319E6"
                    )
                )
            }
            manufacturer == "Samsung" && deviceType == DeviceType.TV -> {
                listOf(
                    IRCode(
                        manufacturer = "Samsung",
                        deviceType = DeviceType.TV,
                        protocol = Protocol.NEC,
                        frequency = 38,
                        pattern = "9000,4500,600,600,600,1800,600,1800,600,600,600,600,600,600,600,600",
                        powerAction = PowerAction.TOGGLE,
                        hexCode = "0xE0E040BF"
                    )
                )
            }
            else -> {
                // Return a basic code for any other combination
                listOf(
                    IRCode(
                        manufacturer = manufacturer,
                        deviceType = deviceType,
                        protocol = Protocol.NEC,
                        frequency = 38,
                        pattern = "9000,4500,560,560,560,1690,560,560,560,560,560,560,560,560,560,560",
                        powerAction = PowerAction.TOGGLE,
                        hexCode = "0x10EF"
                    )
                )
            }
        }
    }

    /**
     * Get all codes for a specific device type
     * Simplified implementation for development
     */
    suspend fun getAllCodesForDeviceType(
        context: Context,
        deviceType: DeviceType
    ): List<IRCode> = withContext(Dispatchers.IO) {
        val manufacturers = getManufacturersForDeviceType(deviceType)
        val allCodes = mutableListOf<IRCode>()

        manufacturers.forEach { manufacturer ->
            allCodes.addAll(getCodesForManufacturer(context, deviceType, manufacturer))
        }

        return@withContext allCodes
    }

    /**
     * Get all TV codes in Quick Mode order (most common brands first)
     * Simplified implementation for development
     */
    suspend fun getTvCodesInQuickModeOrder(context: Context): List<IRCode> = withContext(Dispatchers.IO) {
        val result = mutableListOf<IRCode>()
        val allManufacturers = getManufacturersForDeviceType(DeviceType.TV)

        // Add codes from priority manufacturers first
        for (manufacturer in quickModePriority) {
            if (allManufacturers.contains(manufacturer)) {
                result.addAll(getCodesForManufacturer(context, DeviceType.TV, manufacturer))
            }
        }

        // Add any remaining codes
        for (manufacturer in allManufacturers) {
            if (!quickModePriority.contains(manufacturer)) {
                result.addAll(getCodesForManufacturer(context, DeviceType.TV, manufacturer))
            }
        }

        return@withContext result
    }

    /**
     * Convert a string pattern to timing values
     */
    fun convertPatternToTimings(pattern: String): List<Int> {
        return pattern.split(",").mapNotNull { it.toIntOrNull() }
    }

    /**
     * Special handling for specific device models
     */
    val specialHandlers = mapOf<String, (IRCode) -> ByteArray>(
        // Add any special handlers for specific devices
        "TCL 75Q750G" to { code ->
            // Special handling for TCL 75Q750G
            byteArrayOf(0x57, 0xE3.toByte(), 0x18, 0xE7.toByte())
        }
    )
}