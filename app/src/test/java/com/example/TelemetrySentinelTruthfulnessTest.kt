package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.FieldStatus
import com.example.model.NetraCentralState
import com.example.model.TelemetryFieldState
import com.example.service.TelemetryHealthState
import com.example.service.TelemetrySentinel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TelemetrySentinelTruthfulnessTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun observed(at: Long, thermal: FieldStatus = FieldStatus.LIVE) = NetraCentralState(
        batteryLevel = 50, isDataFresh = true, lastUpdateTimestamp = at,
        fieldStates = TelemetryFieldState(levelStatus = FieldStatus.LIVE, tempStatus = thermal,
            voltageStatus = FieldStatus.LIVE, levelObservedAt = at, tempObservedAt = at, voltageObservedAt = at))

    @Test fun startsUnavailableNotOptimisticallyHealthy() {
        val sentinel = TelemetrySentinel(context) { 1_000L }
        val report = sentinel.healthReport.value
        assertEquals(TelemetryHealthState.UNAVAILABLE, report.state)
        assertEquals(0L, report.lastUpdateTimestamp)
        assertFalse(report.isBatteryActive); assertFalse(report.isThermalActive); assertFalse(report.isBluetoothActive)
    }
    @Test fun verifiedObservationAndRecoveryUpdateHealth() {
        var now = 1_000L; val sentinel = TelemetrySentinel(context) { now }
        sentinel.onTelemetryReceived(observed(now))
        assertEquals(TelemetryHealthState.HEALTHY, sentinel.healthReport.value.state)
        now += 300_001L; sentinel.checkStaleStatus()
        assertEquals(TelemetryHealthState.STALE, sentinel.healthReport.value.state)
        assertEquals(1_000L, sentinel.healthReport.value.lastUpdateTimestamp)
        assertFalse(sentinel.healthReport.value.isBatteryActive)
        sentinel.onTelemetryReceived(observed(now))
        assertEquals(TelemetryHealthState.HEALTHY, sentinel.healthReport.value.state)
    }
    @Test fun retainedOrMissingFieldsDoNotRenewObservationClock() {
        var now = 1_000L; val sentinel = TelemetrySentinel(context) { now }
        sentinel.onTelemetryReceived(observed(now))
        now += 200_000L
        sentinel.onTelemetryReceived(NetraCentralState(batteryLevel = 50, lastUpdateTimestamp = now))
        assertEquals(TelemetryHealthState.DEGRADED, sentinel.healthReport.value.state)
        assertEquals(1_000L, sentinel.healthReport.value.lastUpdateTimestamp)
        now += 100_001L; sentinel.checkStaleStatus()
        assertEquals(TelemetryHealthState.STALE, sentinel.healthReport.value.state)
    }
    @Test fun partialObservationIsDegradedAndBluetoothIsNeverAssumed() {
        val sentinel = TelemetrySentinel(context) { 1_000L }
        sentinel.onTelemetryReceived(observed(1_000L, FieldStatus.LAST_VALID))
        assertEquals(TelemetryHealthState.DEGRADED, sentinel.healthReport.value.state)
        assertTrue(sentinel.healthReport.value.isBatteryActive)
        assertFalse(sentinel.healthReport.value.isThermalActive)
        assertFalse(sentinel.healthReport.value.isBluetoothActive)
    }
}
