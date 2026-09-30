package com.example.model

enum class PowerProfileMode(
    val title: String,
    val subtitle: String,
    val syncIntervalSeconds: Int,
    val brightnessCappedPercent: Int,
    val isAggressiveThrottle: Boolean
) {
    SMART_ADAPTIVE("Smart Adaptive", "Suggests profiles from available battery level and charging state", 60, 80, false),
    BALANCED("Balanced Standard", "Balanced preference; no device controls are applied", 60, 80, false),
    PERFORMANCE("High Performance", "Performance preference; no sampling-rate change is applied", 15, 100, false),
    ENDURANCE("Endurance Saver", "Endurance preference; adjust controls in Android Settings", 300, 60, true),
    ULTRA_SAVER("Ultra Battery Saver", "Saver preference; device-wide restrictions are not applied", 900, 30, true)
}

data class PowerProfileState(
    val selectedMode: PowerProfileMode = PowerProfileMode.SMART_ADAPTIVE,
    val activeEffectiveMode: PowerProfileMode = PowerProfileMode.BALANCED,
    val dynamicSyncThrottled: Boolean = false,
    val adaptiveBrightnessSuggested: Int? = null,
    val backgroundSyncPaused: Boolean = false,
    val lastProfileTransitionReason: String = "Preference only; device controls unavailable"
)
