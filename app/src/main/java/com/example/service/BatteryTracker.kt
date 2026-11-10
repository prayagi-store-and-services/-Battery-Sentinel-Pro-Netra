package com.example.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import com.example.model.BatteryTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battery tracker state and helpers (BS-002 consolidation).
 *
 * The tracking duty used to be a second 24/7 foreground service (BatteryForegroundService)
 * duplicating BatteryMonitorService's receivers and posting the same single status
 * notification. It is now state + pure helpers: BatteryMonitorService is the app's only
 * foreground service and performs the tracking work; the Settings toggle persists here and
 * gates the tracker extras (charging-screen hooks, active power saver hooks).
 */
object BatteryTracker {
    private const val PREFS = "battery_tracker"
    private const val KEY_ENABLED = "enabled"

    private val _batteryStateFlow = MutableStateFlow(BatteryTrackState())
    val batteryState: StateFlow<BatteryTrackState> = _batteryStateFlow.asStateFlow()

    /** Tracker extras default ON, matching the old service which started at every boot. */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        _batteryStateFlow.value = _batteryStateFlow.value.copy(isTrackingActive = enabled)
    }

    /** Single ingestion point, fed by BatteryMonitorService's canonical telemetry. */
    internal fun onTelemetry(context: Context, telemetry: BatteryTelemetry) {
        _batteryStateFlow.value = telemetry.toTrackState(isTrackingActive = isEnabled(context))
    }

    fun isRunning(): Boolean = _batteryStateFlow.value.isTrackingActive

    /**
     * Parses battery percentage, temperature, and charging status from an ACTION_BATTERY_CHANGED intent
     * and the BatteryManager system API.
     */
    fun parseBatteryData(intent: Intent, batteryManager: BatteryManager?): BatteryTrackState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val percentage = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            val directCapacity = try {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            } catch (_: Exception) { -1 }
            if (directCapacity in 0..100) directCapacity else 0
        }

        // EXTRA_TEMPERATURE is in tenths of a degree Celsius (e.g. 285 = 28.5 C)
        val rawTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperatureCelsius = rawTemperature / 10.0f

        val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isChargingDirect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                batteryManager?.isCharging == true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        val isCharging = isChargingDirect || statusInt == BatteryManager.BATTERY_STATUS_CHARGING

        val statusLabel = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> if (isCharging) "Charging" else "Unknown"
        }

        val pluggedInt = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val pluggedSource = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            4 -> "Dock"
            else -> if (isCharging) "Connected" else "Unplugged"
        }

        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        val currentMicroAmps = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        } catch (_: Exception) {
            0
        }

        return BatteryTrackState(
            percentage = percentage,
            temperatureCelsius = temperatureCelsius,
            isCharging = isCharging,
            status = statusLabel,
            pluggedSource = pluggedSource,
            voltageMv = voltageMv,
            currentMicroAmps = currentMicroAmps,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Direct query fallback using BatteryManager system service properties.
     */
    fun queryBatteryManagerDirectly(batteryManager: BatteryManager?): BatteryTrackState {
        val capacity = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        } catch (_: Exception) { 0 }

        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                batteryManager?.isCharging == true
            } catch (_: Exception) { false }
        } else false

        val currentMicroAmps = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        } catch (_: Exception) { 0 }

        return BatteryTrackState(
            percentage = capacity.coerceIn(0, 100),
            temperatureCelsius = 0f,
            isCharging = isCharging,
            status = if (isCharging) "Charging" else "On Battery",
            pluggedSource = if (isCharging) "Connected" else "Unplugged",
            voltageMv = 0,
            currentMicroAmps = currentMicroAmps,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Builds the one unified ongoing notification from a tracking snapshot.
     * BatteryMonitorService posts the same id/channel from its own central state.
     */
    fun buildTrackingNotification(context: Context, state: BatteryTrackState): Notification {
        // Build from the passed snapshot (the caller always passes its freshest one);
        // reading the live central state here would make this function lie about its input.
        val central = com.example.model.NetraCentralState(
            batteryLevel = state.percentage,
            temperatureCelsius = state.temperatureCelsius,
            isCharging = state.isCharging,
            isChargerConnected = state.isCharging || state.pluggedSource != "Unplugged",
            isDataFresh = true
        )
        return SentinelStatusNotification.build(context, central)
    }
}

/**
 * Telemetry model exposed to the Settings toggle and tracker UI.
 */
data class BatteryTrackState(
    val percentage: Int = 0,
    val temperatureCelsius: Float = 0f,
    val isCharging: Boolean = false,
    val status: String = "Unknown",
    val pluggedSource: String = "Unplugged",
    val voltageMv: Int = 0,
    val currentMicroAmps: Int = 0,
    val timestamp: Long = 0L,
    val isTrackingActive: Boolean = false
)

/**
 * Maps the canonical telemetry (single ingestion point in BatteryMonitorService) onto the
 * tracking state. currentMa (milliamps) converts to microamps.
 */
internal fun BatteryTelemetry.toTrackState(isTrackingActive: Boolean): BatteryTrackState =
    BatteryTrackState(
        percentage = level.coerceIn(0, 100),
        temperatureCelsius = temperature,
        isCharging = isCharging,
        status = if (isCharging) "Charging" else "Discharging",
        pluggedSource = when (pluggedType) {
            "AC" -> "AC Charger"
            "USB" -> "USB Port"
            "WIRELESS" -> "Wireless"
            else -> if (isCharging) "Connected" else "Unplugged"
        },
        voltageMv = voltageMv,
        currentMicroAmps = currentMa * 1000,
        timestamp = System.currentTimeMillis(),
        isTrackingActive = isTrackingActive
    )
