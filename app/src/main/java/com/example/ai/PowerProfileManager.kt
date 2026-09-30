package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BatteryTelemetry
import com.example.model.PowerProfileMode
import com.example.model.PowerProfileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PowerProfileManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("netra_power_profile_prefs", Context.MODE_PRIVATE)

    private val _profileState = MutableStateFlow(loadProfileState())
    val profileState: StateFlow<PowerProfileState> = _profileState.asStateFlow()

    private fun loadProfileState(): PowerProfileState {
        val modeName = prefs.getString("selected_profile", PowerProfileMode.SMART_ADAPTIVE.name)
        val mode = try {
            PowerProfileMode.valueOf(modeName ?: PowerProfileMode.SMART_ADAPTIVE.name)
        } catch (_: Exception) {
            PowerProfileMode.SMART_ADAPTIVE
        }

        return PowerProfileState(
            selectedMode = mode,
            activeEffectiveMode = if (mode == PowerProfileMode.SMART_ADAPTIVE) PowerProfileMode.BALANCED else mode,
            dynamicSyncThrottled = false,
            adaptiveBrightnessSuggested = null,
            backgroundSyncPaused = false,
            lastProfileTransitionReason = "Initial profile loaded: ${mode.title}"
        )
    }

    fun setPowerProfile(mode: PowerProfileMode, currentTelemetry: BatteryTelemetry? = null) {
        prefs.edit().putString("selected_profile", mode.name).apply()
        evaluateProfile(mode, currentTelemetry)
    }

    /**
     * Updates a profile suggestion only. No sampling, brightness or account-sync controls are wired.
     */
    fun onTelemetryUpdate(telemetry: BatteryTelemetry) {
        val selected = _profileState.value.selectedMode
        evaluateProfile(selected, telemetry)
    }

    private fun evaluateProfile(selected: PowerProfileMode, telemetry: BatteryTelemetry?) {
        val hasData = telemetry?.isDataAvailable == true && telemetry.level in 0..100
        val level = telemetry?.level
        val isCharging = telemetry?.isCharging ?: false

        val effectiveMode: PowerProfileMode
        val reason: String

        if (selected == PowerProfileMode.SMART_ADAPTIVE) {
            when {
                !hasData -> {
                    effectiveMode = PowerProfileMode.BALANCED
                    reason = "Battery state unavailable; balanced preference only"
                }
                isCharging -> {
                    effectiveMode = PowerProfileMode.PERFORMANCE
                    reason = "Charger connected: performance preference suggested; no controls applied"
                }
                level!! <= 10 -> {
                    effectiveMode = PowerProfileMode.ULTRA_SAVER
                    reason = "Critical battery (≤10%): saver preference suggested; no controls applied"
                }
                level!! <= 20 -> {
                    effectiveMode = PowerProfileMode.ENDURANCE
                    reason = "Low battery (≤20%): endurance preference suggested; no controls applied"
                }
                level!! <= 45 -> {
                    effectiveMode = PowerProfileMode.BALANCED
                    reason = "Moderate battery (≤45%): balanced preference suggested"
                }
                else -> {
                    effectiveMode = PowerProfileMode.BALANCED
                    reason = "Normal battery (>45%): balanced preference suggested"
                }
            }
        } else {
            effectiveMode = selected
            reason = "Selected preference (no controls applied): ${selected.title}"
        }

        val newState = PowerProfileState(
            selectedMode = selected,
            activeEffectiveMode = effectiveMode,
            dynamicSyncThrottled = false,
            adaptiveBrightnessSuggested = null,
            backgroundSyncPaused = false,
            lastProfileTransitionReason = reason
        )

        _profileState.value = newState
    }
}
