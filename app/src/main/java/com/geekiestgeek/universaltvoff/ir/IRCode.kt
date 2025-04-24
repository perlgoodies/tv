package com.geekiestgeek.universaltvoff.ir

/**
 * Core data structure for IR code storage with support for multiple device types
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

/**
 * Device categories
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