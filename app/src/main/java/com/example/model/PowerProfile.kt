package com.example.model

enum class PowerProfileMode(
    val title: String,
    val subtitle: String,
    val syncIntervalSeconds: Int,
    val brightnessCappedPercent: Int,
    val isAggressiveThrottle: Boolean
) {
    SMART_ADAPTIVE("Smart Adaptive", "Suggests profiles from available battery level and charging state", 60, 80, false),
    BALANCED("Balanced Standard", "Restores your automatic brightness, screen timeout and auto-sync", 60, 80, false),
    PERFORMANCE("High Performance", "Restores your automatic brightness, screen timeout and auto-sync", 15, 100, false),
    ENDURANCE("Endurance Saver", "Dims to 40%, screen timeout 30 s (restored when you switch back)", 300, 60, true),
    ULTRA_SAVER("Ultra Battery Saver", "Dims to 15%, screen timeout 15 s, auto-sync off (restored when you switch back)", 900, 30, true)
}

data class PowerProfileState(
    val selectedMode: PowerProfileMode = PowerProfileMode.SMART_ADAPTIVE,
    val activeEffectiveMode: PowerProfileMode = PowerProfileMode.BALANCED,
    val dynamicSyncThrottled: Boolean = false,
    val adaptiveBrightnessSuggested: Int? = null,
    val backgroundSyncPaused: Boolean = false,
    val lastProfileTransitionReason: String = "Preference only; device controls unavailable",
    val syncResult: String = "Not applied",
    val brightnessResult: String = "Not applied",
    val timeoutResult: String = "Not applied"
)
