package com.example.model

/**
 * Timestamped electrical sample recorded during an active charging session.
 */
data class LiveChargingSample(
    val timestamp: Long,
    val voltageV: Float? = null,
    val currentA: Float? = null,
    val powerWatts: Float? = null,
    val batteryPercent: Float? = null,
    val pluggedSource: String? = null,
    val isValid: Boolean = true,
    val voltageMv: Float? = voltageV?.let { it * 1000f },
    val currentMa: Float? = currentA?.let { it * 1000f },
    val temperatureCelsius: Float? = null
)

/**
 * State representing the live charging session.
 * Active strictly while the device is confirmed charging.
 */
data class LiveChargingSessionState(
    val isChargingActive: Boolean = false,
    val sessionStartTimeMs: Long? = null,
    val sessionElapsedRealtimeMs: Long? = null,
    val sessionDurationSeconds: Long = 0L,
    val currentVoltageV: Float? = null,
    val currentCurrentA: Float? = null,
    val currentVoltageMv: Float? = currentVoltageV?.let { it * 1000f },
    val currentCurrentMa: Float? = currentCurrentA?.let { it * 1000f },
    val currentPowerWatts: Float? = null,
    val currentBatteryPercent: Float? = null,
    val currentTemperatureCelsius: Float? = null,
    val pluggedSource: String? = null, // "AC", "USB", "WIRELESS", etc.
    val lastUpdatedTimeMs: Long? = null,
    val rollingHistory: List<LiveChargingSample> = emptyList(), // Bounded up to 300 entries
    val isDischarging: Boolean = false,
    val estimatedTimeToFullSeconds: Long? = null,
    val isFull: Boolean = false,
    // Discharge (on battery) live data. Kept apart from the charging fields so the two flows are never mixed.
    val dischargeVoltageMv: Float? = null,
    val dischargeCurrentMa: Float? = null,
    val dischargePowerWatts: Float? = null,
    val dischargeHistory: List<LiveChargingSample> = emptyList(), // Bounded up to 300 entries
    val dischargeDurationSeconds: Long = 0L,
    val etaDisplayStatus: String? = null // e.g. "Calculating...", "Unavailable", "00:00:00", or formatted "HH:mm:ss"
)
