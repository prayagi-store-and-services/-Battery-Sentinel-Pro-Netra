package com.example.service

import org.junit.Assert.*
import org.junit.Test

class ContinuousChargeAlertTest {

    @Test
    fun milestoneBelowTargetIsNull() {
        assertNull(ContinuousChargeAlert.milestoneFor(0, 80))
        assertNull(ContinuousChargeAlert.milestoneFor(50, 80))
        assertNull(ContinuousChargeAlert.milestoneFor(79, 80))
        assertNull(ContinuousChargeAlert.milestoneFor(90, 95))
        assertNull(ContinuousChargeAlert.milestoneFor(94, 95))
        assertNull(ContinuousChargeAlert.milestoneFor(99, 100))
    }

    @Test
    fun milestoneMatchesHighestStepAtOrAboveTarget() {
        assertEquals(80, ContinuousChargeAlert.milestoneFor(80, 80))
        assertEquals(80, ContinuousChargeAlert.milestoneFor(84, 80))
        assertEquals(85, ContinuousChargeAlert.milestoneFor(85, 80))
        assertEquals(90, ContinuousChargeAlert.milestoneFor(92, 80))
        assertEquals(95, ContinuousChargeAlert.milestoneFor(99, 80))
        assertEquals(100, ContinuousChargeAlert.milestoneFor(100, 80))
        // With a 95 target the steps are 95 and 100 only.
        assertEquals(95, ContinuousChargeAlert.milestoneFor(95, 95))
        assertEquals(95, ContinuousChargeAlert.milestoneFor(99, 95))
        assertEquals(100, ContinuousChargeAlert.milestoneFor(100, 95))
    }

    @Test
    fun belowTargetNeverAlertsEvenWhileCharging() {
        // Regression: a 95 target must stay silent at 80/85/90, even plugged in and charging.
        assertFalse(ContinuousChargeAlert.shouldAlert(80, true, true, enabled = true, targetPercent = 95))
        assertFalse(ContinuousChargeAlert.shouldAlert(85, true, true, enabled = true, targetPercent = 95))
        assertFalse(ContinuousChargeAlert.shouldAlert(89, true, true, enabled = true, targetPercent = 95))
        assertFalse(ContinuousChargeAlert.shouldAlert(94, true, true, enabled = true, targetPercent = 95))
        assertFalse(ContinuousChargeAlert.shouldAlert(50, true, true, enabled = true, targetPercent = 80))
        assertFalse(ContinuousChargeAlert.shouldAlert(79, true, true, enabled = true, targetPercent = 80))
    }

    @Test
    fun alertStartsExactlyAtTarget() {
        assertTrue(ContinuousChargeAlert.shouldAlert(95, true, true, enabled = true, targetPercent = 95))
        assertTrue(ContinuousChargeAlert.shouldAlert(96, true, true, enabled = true, targetPercent = 95))
        assertTrue(ContinuousChargeAlert.shouldAlert(100, true, true, enabled = true, targetPercent = 95))
        assertTrue(ContinuousChargeAlert.shouldAlert(80, true, true, enabled = true, targetPercent = 80))
    }

    @Test
    fun pluggedInWithoutChargingStillAlerts() {
        // Phones often pause charging near a limit; plugged in still counts (same rule as the target alert).
        assertTrue(ContinuousChargeAlert.shouldAlert(90, false, true, enabled = true, targetPercent = 90))
    }

    @Test
    fun unpluggedStopsTheAlert() {
        assertFalse(ContinuousChargeAlert.shouldAlert(90, false, false, enabled = true, targetPercent = 80))
        assertFalse(ContinuousChargeAlert.shouldAlert(100, false, false, enabled = true, targetPercent = 80))
        assertFalse(ContinuousChargeAlert.shouldAlert(90, null, null, enabled = true, targetPercent = 80))
    }

    @Test
    fun unknownLevelNeverAlerts() {
        assertFalse(ContinuousChargeAlert.shouldAlert(null, true, true, enabled = true, targetPercent = 80))
    }

    @Test
    fun disabledSwitchStopsTheAlert() {
        assertFalse(ContinuousChargeAlert.shouldAlert(95, true, true, enabled = false, targetPercent = 80))
    }

    @Test
    fun targetAboveEveryStepNeverAlerts() {
        assertNull(ContinuousChargeAlert.milestoneFor(100, 101))
        assertFalse(ContinuousChargeAlert.shouldAlert(100, true, true, enabled = true, targetPercent = 101))
    }
}
