package com.example

import com.example.model.*
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CentralPowerWorkContextTest {
    private fun live() = NetraCentralState(batteryLevel = 50, isDataFresh = true,
        fieldStates = TelemetryFieldState(levelStatus = FieldStatus.LIVE))
    private fun context(s: NetraCentralState, observed: Long? = 10L, fresh: Boolean = true) =
        CentralPowerWorkContext.fromSnapshot(s, observed, 11L, fresh, false, true)

    @Test fun missingOrHistoricalLevelCannotBecomeAnAvailableObservation() {
        for (level in listOf(null, -1, 101)) assertFalse(context(live().copy(batteryLevel = level)).batteryLevelAvailable)
        for (status in FieldStatus.values()) assertEquals(status == FieldStatus.LIVE,
            context(live().copy(fieldStates = TelemetryFieldState(levelStatus = status))).batteryLevelAvailable)
        for (level in listOf(0, 100)) assertTrue(context(live().copy(batteryLevel = level)).batteryLevelAvailable)
    }
    @Test fun freshnessRequiresBothCentralAndAcquisitionEvidence() {
        assertTrue(context(live()).stateFresh)
        assertFalse(context(live(), fresh = false).stateFresh)
        assertFalse(context(live().copy(isDataFresh = false)).stateFresh)
    }
    @Test fun wallTimeDoesNotManufactureMonotonicProvenance() {
        val c = context(live().copy(lastUpdateTimestamp = Long.MAX_VALUE), observed = null)
        assertNull(c.observedAtElapsedMillis)
        assertEquals(PowerWorkReason.STALE_OR_UNORDERED_STATE,
            PowerWorkDecisionPolicy.evaluate(PowerWorkImportance.OPTIONAL, c).reason)
    }
    @Test fun restrictionFlagsAreReadOnlyAndChargingDoesNotBypassThem() {
        for (thermal in listOf(false, true)) for (low in listOf(false, true)) {
            val s = live().copy(isCriticalThermalActive = thermal, isLowBatteryControlActive = low, isCharging = true)
            val before = s.copy()
            assertEquals(thermal || low, context(s).existingRestrictionActive)
            assertEquals(before, s)
        }
    }
    @Test fun confirmedScreenOffWinsOverDefaultScreenOnWithoutNewVisibilityInference() {
        val s = live().copy(isScreenOn = true, isScreenOffConfirmed = true)
        assertFalse(context(s).screenOn)
        val c = CentralPowerWorkContext.fromSnapshot(s, 10, 11, true, null, false)
        assertNull(c.inDoze)
        assertFalse(c.appVisible)
    }
}
