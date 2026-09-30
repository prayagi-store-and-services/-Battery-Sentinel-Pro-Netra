package com.example

import android.os.BatteryManager
import com.example.service.SessionEtaEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionEtaEstimatorTest {
    private val charging = BatteryManager.BATTERY_STATUS_CHARGING
    private val discharge = BatteryManager.BATTERY_STATUS_DISCHARGING
    private val ac = BatteryManager.BATTERY_PLUGGED_AC
    private fun SessionEtaEstimator.charge(level: Int?, min: Int, plug: Int = ac) = observe(level, charging, plug, min * 60_000L)
    private fun SessionEtaEstimator.drain(level: Int?, min: Int) = observe(level, discharge, 0, min * 60_000L)

    @Test fun sameSessionProgressionProducesOnlyCorrectDirection() {
        val e = SessionEtaEstimator()
        assertNull(e.charge(50, 0).chargingMinutes)
        val c = e.charge(52, 2)
        assertEquals(48, c.chargingMinutes); assertNull(c.dischargingMinutes)
        assertNull(e.drain(52, 3).dischargingMinutes)
        val d = e.drain(50, 5)
        assertEquals(50, d.dischargingMinutes); assertNull(d.chargingMinutes)
    }
    @Test fun reconnectAndChargerTypeChangesResetEvidence() {
        val e = SessionEtaEstimator(); e.charge(50, 0); e.charge(52, 2)
        e.drain(51, 3)
        assertNull(e.charge(52, 4).chargingMinutes)
        e.charge(54, 6)
        assertNull(e.charge(55, 7, BatteryManager.BATTERY_PLUGGED_USB).chargingMinutes)
    }
    @Test fun pausedFullUnknownAndMissingObservationsClearEstimate() {
        for (status in listOf(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_STATUS_UNKNOWN, -1)) {
            val e = SessionEtaEstimator(); e.charge(50, 0); e.charge(52, 2)
            assertNull(e.observe(53, status, ac, 180_000).chargingMinutes)
            assertNull(e.charge(54, 4).chargingMinutes)
        }
        val e = SessionEtaEstimator(); e.charge(50, 0); e.charge(52, 2)
        assertNull(e.charge(null, 3).chargingMinutes)
        assertNull(e.charge(54, 4).chargingMinutes)
    }
    @Test fun gapsClockReversalAndLevelReversalStartNewEvidence() {
        val e = SessionEtaEstimator(); e.charge(50, 0); e.charge(52, 2)
        assertNull(e.charge(53, 33).chargingMinutes)
        e.charge(55, 35)
        assertNull(e.charge(56, 34).chargingMinutes)
        e.charge(58, 36)
        assertNull(e.charge(57, 37).chargingMinutes)
    }
    @Test fun insufficientFlatOrUnsupportedRangeIsUnavailableNotClamped() {
        val e = SessionEtaEstimator(); e.charge(50, 0)
        assertNull(e.charge(51, 1).chargingMinutes)
        val flat = SessionEtaEstimator(); flat.charge(50, 0)
        assertNull(flat.charge(50, 4).chargingMinutes)
        val slow = SessionEtaEstimator(); slow.charge(1, 0)
        assertNull(slow.charge(2, 20).chargingMinutes)
        val full = SessionEtaEstimator(); full.charge(98, 0)
        assertNull(full.charge(100, 2).chargingMinutes)
    }
}
