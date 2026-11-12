package com.example

import com.example.model.*
import com.example.ui.components.ChargingScreenDisplayPolicy as P
import org.junit.Assert.*
import org.junit.Test

class ChargingScreenDisplayPolicyTest {
    private fun sample(at:Long,speed:CanonicalChargingSpeed=CanonicalChargingSpeed.SLOW)=NetraCentralState(
        isCharging=true,isChargerConnected=true,chargerConnectedAt=1L,chargingSpeed=speed,isDataFresh=true,lastUpdateTimestamp=at,
        fieldStates=TelemetryFieldState(powerStatus=FieldStatus.LIVE,powerObservedAt=at))
    @Test fun nightBoundaries() {
        assertFalse(P.night(22));assertTrue(P.night(23));assertTrue(P.night(0));assertTrue(P.night(5));assertFalse(P.night(6))
    }
    @Test fun allSpeedsAreBlockedAtNight() {
        for (speed in CanonicalChargingSpeed.values()) assertFalse(P().allowed(sample(100L,speed),0,100,23))
    }
    @Test fun slowNeedsFullMinuteAndAnotherActualSample() {
        val p=P();assertTrue(p.allowed(sample(100L),0,100,12))
        assertTrue(p.allowed(sample(60_099L),59_999,60_099,12))
        assertFalse(p.allowed(sample(60_100L),60_000,60_100,12))
    }
    @Test fun uiTicksCannotPretendAnotherMeasurement() {
        val p=P();assertTrue(p.allowed(sample(100L),0,100,12))
        assertTrue(p.allowed(sample(100L),60_000,60_100,12))
    }
    @Test fun normalAndFastNeverStartSlowTimer() {
        for (speed in listOf(CanonicalChargingSpeed.NORMAL,CanonicalChargingSpeed.FAST,CanonicalChargingSpeed.SUPER_FAST,CanonicalChargingSpeed.ULTRA_FAST)) {
            val p=P();assertTrue(p.allowed(sample(100L,speed),0,100,12));assertTrue(p.allowed(sample(99_100L,speed),99_000,99_100,12))
        }
    }
    @Test fun unknownOrStaleSpeedCannotTriggerSlowClose() {
        val p=P();p.allowed(sample(100L),0,100,12)
        assertTrue(p.allowed(sample(60_100L).copy(isDataFresh=false),60_000,60_100,12))
        assertTrue(p.allowed(sample(60_101L),60_001,60_101,12))
        assertTrue(p.allowed(sample(120_101L,CanonicalChargingSpeed.UNAVAILABLE),120_001,120_101,12))
    }
    @Test fun retainedOrOldPowerCannotTriggerSlowClose() {
        val p=P();p.allowed(sample(100L),0,100,12)
        assertTrue(p.allowed(sample(60_100L).copy(fieldStates=TelemetryFieldState(powerStatus=FieldStatus.LAST_VALID,powerObservedAt=100L)),60_000,60_100,12))
        assertTrue(p.allowed(sample(100L),100_000,100_100,12))
    }
    @Test fun unknownDoesNotReopenAlreadySuppressedSlowSession() {
        val p=P();p.allowed(sample(100L),0,100,12);assertFalse(p.allowed(sample(60_100L),60_000,60_100,12))
        assertFalse(p.allowed(sample(60_101L,CanonicalChargingSpeed.UNAVAILABLE),60_001,60_101,12))
        assertTrue(p.allowed(sample(60_102L,CanonicalChargingSpeed.NORMAL),60_002,60_102,12))
    }
    @Test fun newConnectionAndDisconnectResetSlowState() {
        val p=P();p.allowed(sample(100L),0,100,12);p.allowed(sample(60_100L),60_000,60_100,12)
        assertTrue(p.allowed(sample(60_101L).copy(chargerConnectedAt=60_101L),60_001,60_101,12))
        p.allowed(sample(120_101L).copy(chargerConnectedAt=60_101L),120_001,120_101,12)
        assertTrue(p.allowed(sample(120_102L).copy(isCharging=false,isChargerConnected=false),120_002,120_102,12))
    }
    @Test fun monotonicClockRollbackCannotCreateSlowDuration() {
        val p=P();p.allowed(sample(100L),100_000,100,12)
        assertTrue(p.allowed(sample(60_100L),1,60_100,12))
    }
    @Test fun wallClockRollbackCannotMakeStaleSpeedLive() {
        val p=P();p.allowed(sample(100L),0,100,12)
        assertTrue(p.allowed(sample(90_100L),90_000,1000,12))
    }
    @Test fun manualResetRearmsPolicy() {
        val p=P();p.allowed(sample(100L),0,100,12);p.allowed(sample(60_100L),60_000,60_100,12);p.reset()
        assertTrue(p.allowed(sample(60_101L),60_001,60_101,12))
    }
    @Test fun sparseSamplesCannotInventContinuousSlowDuration() {
        val p=P();p.allowed(sample(100L),0,100,12)
        assertTrue(p.allowed(sample(300_100L),300_000,300_100,12))
        assertTrue(p.allowed(sample(330_100L),330_000,330_100,12))
        assertFalse(p.allowed(sample(360_100L),360_000,360_100,12))
    }

}
