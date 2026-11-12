package com.example

import android.os.BatteryManager as B
import com.example.model.NetraCentralState
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalSessionTimeTest {
    private fun snapshot(charging: Boolean = true) = BatterySnapshot(
        if (charging) B.BATTERY_STATUS_CHARGING else B.BATTERY_STATUS_DISCHARGING,
        if (charging) 1 else 0, 50, 100, 4000, 300, 1000000)

    @Test fun onlyMatchingCentralModeCanSupplyStart() {
        val charge = NetraCentralState(isCharging = true, isChargerConnected = true, chargingStartedAt = 100L, dischargingStartedAt = 50L)
        assertEquals(100L, CanonicalSessionTime.startEpochMs(charge, true))
        assertNull(CanonicalSessionTime.startEpochMs(charge, false))
        val discharge = charge.copy(isCharging = false, isChargerConnected = false)
        assertEquals(50L, CanonicalSessionTime.startEpochMs(discharge, false))
        assertNull(CanonicalSessionTime.startEpochMs(discharge, true))
        assertNull(CanonicalSessionTime.startEpochMs(discharge.copy(isChargerConnected = true), false))
        assertNull(CanonicalSessionTime.startEpochMs(NetraCentralState(), true))
    }
    @Test fun invalidEpochCannotBecomeDuration() {
        for (s in listOf(null, 0L, -1L, 1001L)) assertNull(CanonicalSessionTime.validStart(s, 1000L))
        assertEquals(1L, CanonicalSessionTime.validStart(1L, 1000L))
    }
    @Test fun negativeElapsedStartAfterRebootPreservesAge() {
        val start = CanonicalSessionTime.elapsedStart(1000L, 7201000L, 10000L)
        assertTrue(start < 0)
        assertEquals(7200000L, 10000L - start)
    }
    @Test fun cardDurationSurvivesProcessRecreationWithoutInventingEta() {
        fun process() = LivePowerTelemetry({ 1000000L }, { 8200000L })
        val a = process().update(snapshot(), 10000L)
        val b = process().update(snapshot(), 20000L)
        assertEquals("02:00:00", a.sessionDuration)
        assertEquals(a.sessionDuration, b.sessionDuration)
        assertEquals("Calculating...", b.estimate)
    }
    @Test fun dischargeDurationUsesSamePersistedIdentity() {
        val t = LivePowerTelemetry({ mode -> if (mode == PowerMode.DISCHARGING) 1000L else null }, { 3601000L })
        assertEquals("01:00:00", t.update(snapshot(false), 100L).sessionDuration)
    }
    @Test fun invalidStartFallsBackToObservedTime() {
        val t = LivePowerTelemetry({ 9000L }, { 8000L })
        assertEquals("00:00:00", t.update(snapshot(), 1000L).sessionDuration)
        assertEquals("00:00:03", t.update(snapshot(), 4000L).sessionDuration)
    }
    @Test fun canonicalStartCanArriveAfterFirstSample() {
        var start: Long? = null
        val t = LivePowerTelemetry({ start }, { 3601000L })
        assertEquals("00:00:00", t.update(snapshot(), 1000L).sessionDuration)
        start = 1000L
        assertEquals("01:00:00", t.update(snapshot(), 2000L).sessionDuration)
    }
    @Test fun establishedAnchorUsesMonotonicClockDespiteWallClockJump() {
        var wall = 3601000L
        val t = LivePowerTelemetry({ 1000L }, { wall })
        t.update(snapshot(), 1000L)
        wall += 3600000L
        assertEquals("01:00:03", t.update(snapshot(), 4000L).sessionDuration)
    }
    @Test fun changedCanonicalSessionResetsDurationEvenWhenModeUnchanged() {
        var start = 1000L
        val t = LivePowerTelemetry({ start }, { 3601000L })
        t.update(snapshot(), 1000L)
        start = 3600000L
        assertEquals("00:00:01", t.update(snapshot(), 2000L).sessionDuration)
    }
}
