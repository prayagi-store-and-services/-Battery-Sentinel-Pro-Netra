package com.example.model

/**
 * Timestamped electrical sample recorded during an active charging session.
 */
data class LiveChargingSample(
    val timestamp: Long,
    val voltageV: Float?,
    val currentA: Float?,
    val powerWatts: Float?,
    val batteryPercent: Float?,
    val pluggedSource: String?,
    val isValid: Boolean = true
)

/**
 * State representing the live charging session.
 * Active strictly while the device is confirmed charging.
 */
data class LiveChargingSessionState(
    val isChargingActive: Boolean = false,
    val sessionStartTimeMs: Long? = null,
    val sessionDurationSeconds: Long = 0L,
    val currentVoltageV: Float? = null,
    val currentCurrentA: Float? = null,
    val currentPowerWatts: Float? = null,
    val currentBatteryPercent: Float? = null,
    val pluggedSource: String? = null, // "AC", "USB", "WIRELESS", etc.
    val lastUpdatedTimeMs: Long? = null,
    val rollingHistory: List<LiveChargingSample> = emptyList(), // Bounded up to 300 entries
    val isDischarging: Boolean = false
)
