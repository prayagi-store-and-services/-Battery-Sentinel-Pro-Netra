package com.example.service

import android.content.Context
import android.util.Log
import com.example.NetraApplication
import com.example.model.NetraCentralState
import com.example.model.FieldStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TelemetryHealthState {
    HEALTHY,
    DEGRADED,
    STALE,
    UNAVAILABLE,
    FAILED,
    RECOVERING
}

data class TelemetryHealthReport(
    val state: TelemetryHealthState,
    val lastUpdateTimestamp: Long,
    val isBatteryActive: Boolean,
    val isThermalActive: Boolean,
    val isBluetoothActive: Boolean,
    val message: String
)

/**
 * Telemetry & Runtime Sentinel:
 * - Supervises the existing single-source battery, thermal, and Bluetooth telemetry pipeline.
 * - Detects stale or missing telemetry, monitors collector health, and logs significant events.
 * - Ensures 24/7 background monitoring recoverability without creating duplicate polling loops or duplicate announcements.
 */
class TelemetrySentinel(private val context: Context, private val clock: () -> Long = { System.currentTimeMillis() }) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _healthReport = MutableStateFlow(
        TelemetryHealthReport(
            state = TelemetryHealthState.UNAVAILABLE,
            lastUpdateTimestamp = 0L,
            isBatteryActive = false,
            isThermalActive = false,
            isBluetoothActive = false,
            message = "Waiting for a verified battery observation."
        )
    )
    val healthReport: StateFlow<TelemetryHealthReport> = _healthReport.asStateFlow()

    private var lastTelemetryUpdateMs: Long = 0L
    private var lastLoggedState: TelemetryHealthState = TelemetryHealthState.UNAVAILABLE
    private val staleThresholdMs: Long = 300_000L // 5 minutes

    /**
     * Called by the canonical BatteryMonitorService whenever new telemetry is received.
     */
    @Synchronized
    fun onTelemetryReceived(state: NetraCentralState) {
        val batteryLive = state.isDataFresh && state.fieldStates.levelStatus == FieldStatus.LIVE
        val thermalLive = state.fieldStates.tempStatus == FieldStatus.LIVE
        // Invalid or repeated retained snapshots do not reset the stale clock.
        if (batteryLive && state.fieldStates.levelObservedAt > lastTelemetryUpdateMs) {
            lastTelemetryUpdateMs = state.fieldStates.levelObservedAt
        }
        val health = when {
            !batteryLive && lastTelemetryUpdateMs == 0L -> TelemetryHealthState.UNAVAILABLE
            !batteryLive -> TelemetryHealthState.DEGRADED
            !thermalLive || state.fieldStates.voltageStatus != FieldStatus.LIVE -> TelemetryHealthState.DEGRADED
            else -> TelemetryHealthState.HEALTHY
        }
        publishHealth(health, batteryLive, thermalLive)
    }

    /** Called from the existing service supervisor, never a second polling loop. */
    @Synchronized
    fun checkStaleStatus() {
        val now = clock()
        if (lastTelemetryUpdateMs == 0L) {
            publishHealth(TelemetryHealthState.UNAVAILABLE, false, false)
        } else if (now < lastTelemetryUpdateMs || now - lastTelemetryUpdateMs > staleThresholdMs) {
            publishHealth(TelemetryHealthState.STALE, false, false)
        }
    }

    private fun publishHealth(state: TelemetryHealthState, batteryLive: Boolean, thermalLive: Boolean) {
        val message = when (state) {
            TelemetryHealthState.HEALTHY -> "Verified canonical battery and thermal observation received."
            TelemetryHealthState.DEGRADED -> "Some canonical fields are retained or unavailable; not fully live."
            TelemetryHealthState.STALE -> "No verified battery observation within the last five minutes."
            else -> "Waiting for a verified battery observation."
        }
        val current = _healthReport.value
        _healthReport.value = current.copy(
            state = state, lastUpdateTimestamp = lastTelemetryUpdateMs,
            isBatteryActive = batteryLive, isThermalActive = thermalLive,
            // This sentinel does not verify the Bluetooth collector. Do not invent an active status.
            isBluetoothActive = false, message = message
        )
        if (state != lastLoggedState) {
            lastLoggedState = state
            logSentinelEventToDb(state, message)
        }
    }

    private fun logSentinelEventToDb(state: TelemetryHealthState, message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val repository = NetraApplication.instance.batteryRepository
                val severity = when (state) {
                    TelemetryHealthState.HEALTHY -> "INFO"
                    TelemetryHealthState.DEGRADED, TelemetryHealthState.STALE -> "WARNING"
                    TelemetryHealthState.UNAVAILABLE, TelemetryHealthState.FAILED -> "CRITICAL"
                    TelemetryHealthState.RECOVERING -> "INFO"
                }
                repository.logEvent(
                    title = "Runtime Sentinel [$state]",
                    message = message,
                    category = "SENTINEL",
                    severity = severity,
                    dotColor = when (severity) {
                        "CRITICAL" -> "RED"
                        "WARNING" -> "AMBER"
                        else -> "CYAN"
                    }
                )
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "TelemetrySentinel"
    }
}
