package com.geekiestgeek.universaltvoff.ir

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility class for parsing IR code data from text files
 */
object IRCodeParser {

    private const val TAG = "IRCodeParser"

    /**
     * Load IR codes from an asset file using the compact format
     */
    fun loadCodesFromAssets(context: Context, fileName: String): List<IRCode> {
        val codes = mutableListOf<IRCode>()
        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))

            reader.forEachLine { line ->
                try {
                    parseLine(line)?.let { codes.add(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing line: $line, ${e.message}")
                }
            }

            reader.close()

            Log.d(TAG, "Loaded ${codes.size} codes from $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading codes from $fileName: ${e.message}")
        }
        return codes
    }

    /**
     * Parse a single line of code data
     * Format: DeviceType|Manufacturer|Protocol|Frequency|Pattern|PowerAction|HexCode|Model|Bits
     */
    private fun parseLine(line: String): IRCode? {
        // Skip blank lines and comments
        if (line.isBlank() || line.startsWith("#")) {
            return null
        }

        val parts = line.split("|")
        if (parts.size < 7) {
            Log.w(TAG, "Invalid format, needs at least 7 parts: $line")
            return null
        }

        try {
            val deviceType = DeviceType.valueOf(parts[0])
            val manufacturer = parts[1]
            val protocol = Protocol.valueOf(parts[2])
            val frequency = parts[3].toInt()
            val pattern = parts[4]
            val powerAction = PowerAction.valueOf(parts[5])
            val hexCode = parts[6]
            val model = if (parts.size > 7) parts[7] else ""
            val bits = if (parts.size > 8) parts[8].toInt() else 32

            return IRCode(
                manufacturer = manufacturer,
                model = model,
                deviceType = deviceType,
                protocol = protocol,
                frequency = frequency,
                pattern = pattern,
                powerAction = powerAction,
                hexCode = hexCode,
                bits = bits
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing line: $line, ${e.message}")
            return null
        }
    }
}