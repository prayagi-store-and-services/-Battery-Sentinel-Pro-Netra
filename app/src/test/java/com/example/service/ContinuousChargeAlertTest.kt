package com.example.service

import org.junit.Assert.*
import org.junit.Test

class ContinuousChargeAlertTest {

    @Test
    fun milestoneBelowLowestLevelIsNull() {
        assertNull(ContinuousChargeAlert.milestoneFor(0))
        assertNull(ContinuousChargeAlert.milestoneFor(50))
        assertNull(ContinuousChargeAlert.milestoneFor(79))
    }

    @Test
    fun milestoneMatchesHighestReachedLevel() {
        assertEquals(80, ContinuousChargeAlert.milestoneFor(80))
        assertEquals(80, ContinuousChargeAlert.milestoneFor(84))
        assertEquals(85, ContinuousChargeAlert.milestoneFor(85))
        assertEquals(90, ContinuousChargeAlert.milestoneFor(92))
        assertEquals(95, ContinuousChargeAlert.milestoneFor(99))
        assertEquals(100, ContinuousChargeAlert.milestoneFor(100))
    }

    @Test
    fun alertRunsWhenChargingAtAlertLevel() {
        assertTrue(ContinuousChargeAlert.shouldAlert(80, true, true, enabled = true))
        assertTrue(ContinuousChargeAlert.shouldAlert(100, true, true, enabled = true))
        assertTrue(ContinuousChargeAlert.shouldAlert(96, true, true, enabled = true))
    }

    @Test
    fun pluggedInWithoutChargingStillAlerts() {
        // Phones often pause charging near a limit; plugged in still counts (same rule as the target alert).
        assertTrue(ContinuousChargeAlert.shouldAlert(90, false, true, enabled = true))
    }

    @Test
    fun unpluggedStopsTheAlert() {
        assertFalse(ContinuousChargeAlert.shouldAlert(90, false, false, enabled = true))
        assertFalse(ContinuousChargeAlert.shouldAlert(100, false, false, enabled = true))
        assertFalse(ContinuousChargeAlert.shouldAlert(90, null, null, enabled = true))
    }

    @Test
    fun belowLowestLevelNeverAlerts() {
        assertFalse(ContinuousChargeAlert.shouldAlert(79, true, true, enabled = true))
        assertFalse(ContinuousChargeAlert.shouldAlert(15, true, true, enabled = true))
    }

    @Test
    fun unknownLevelNeverAlerts() {
        assertFalse(ContinuousChargeAlert.shouldAlert(null, true, true, enabled = true))
    }

    @Test
    fun disabledSwitchStopsTheAlert() {
        assertFalse(ContinuousChargeAlert.shouldAlert(95, true, true, enabled = false))
    }
}
