package com.example.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CanonicalLiveChargingSessionTest {
    @Test fun recreatedChargeEngineUsesPersistedStartEvenAfterReboot() = runTest {
        repeat(2) {
            val hw = FakeBatteryHardwareProvider(isChargingState = true, pluggedTypeState = "AC")
            val engine = LiveChargingSessionEngine(hw, this, { 8200000L }, { 10000L }, canonicalSessionStartEpochMs = { if (it) 1000000L else null })
            engine.onScreenResumed(); runCurrent()
            assertEquals(7200L, engine.sessionState.value.sessionDurationSeconds)
            assertEquals(1000000L, engine.sessionState.value.sessionStartTimeMs)
            assertEquals("Calculating...", engine.sessionState.value.etaDisplayStatus)
            engine.onScreenPaused(); runCurrent()
        }
    }
    @Test fun canonicalDischargeWinsOverOldIndependentAnchor() = runTest {
        val old = object : DischargeAnchorStore {
            override fun get() = 8000000L
            override fun set(epochMs: Long) {}
            override fun clear() {}
        }
        val engine = LiveChargingSessionEngine(FakeBatteryHardwareProvider(), this, { 8200000L }, { 10000L }, anchorStore = old, canonicalSessionStartEpochMs = { if (!it) 1000000L else null })
        engine.onScreenResumed(); runCurrent()
        assertEquals(7200L, engine.sessionState.value.dischargeDurationSeconds)
        engine.onScreenPaused(); runCurrent()
    }
    @Test fun futureCanonicalChargeStartIsNotUsed() = runTest {
        val engine = LiveChargingSessionEngine(FakeBatteryHardwareProvider(isChargingState = true), this, { 1000L }, { 100L }, canonicalSessionStartEpochMs = { 2000L })
        engine.onScreenResumed(); runCurrent()
        assertEquals(0L, engine.sessionState.value.sessionDurationSeconds)
        assertEquals(1000L, engine.sessionState.value.sessionStartTimeMs)
        engine.onScreenPaused(); runCurrent()
    }
}
