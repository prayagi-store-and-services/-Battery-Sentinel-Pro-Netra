package com.example

import com.example.service.RiseDetector
import com.example.service.SaverDecision
import com.example.service.SaverPolicy
import org.junit.Assert.*
import org.junit.Test

class RiseDetectorTest {
    private fun feed(d: RiseDetector, temps: List<Float>, stepMs: Long = 30_000L, charging: Boolean = false) {
        temps.forEachIndexed { i, t -> d.add(1_000_000L + i * stepMs, t, charging) }
    }

    @Test fun fastRiseIsDetected() {
        val d = RiseDetector(); feed(d, listOf(33.0f, 33.4f, 33.9f, 34.4f))
        assertTrue(d.rapid())
        assertTrue(d.ratePerMin()!! >= 0.4f)
    }
    @Test fun slowRiseIsNotDetected() {
        val d = RiseDetector(); feed(d, listOf(33.0f, 33.1f, 33.2f, 33.3f, 33.4f, 33.5f))
        assertFalse(d.rapid())
    }
    @Test fun coolPhoneIsNotDetectedEvenIfRisingFast() {
        val d = RiseDetector(); feed(d, listOf(28.0f, 28.6f, 29.2f, 29.8f))
        assertFalse(d.rapid())
    }
    @Test fun notEnoughSamplesOrSpanIsNotDetected() {
        val d = RiseDetector(); feed(d, listOf(33.0f, 35.0f))
        assertFalse(d.rapid())
        val e = RiseDetector(); feed(e, listOf(33.0f, 34.0f, 35.0f), stepMs = 20_000L)
        assertNull(e.ratePerMin())
        assertFalse(e.rapid())
    }
    @Test fun chargingNeverCountsAndResets() {
        val d = RiseDetector(); feed(d, listOf(33.0f, 34.0f, 35.0f, 36.0f), charging = true)
        assertFalse(d.rapid())
        // plug in after a rise: window is cleared, so unplugging starts fresh
        val e = RiseDetector(); feed(e, listOf(33.0f, 33.5f, 34.0f, 34.5f))
        assertTrue(e.rapid())
        e.add(1_200_000L, 35f, true)
        e.add(1_230_000L, 35f, false)
        assertFalse(e.rapid())
    }
    @Test fun staleGapClearsWindow() {
        val d = RiseDetector(); feed(d, listOf(33.0f, 33.4f, 33.9f))
        d.add(1_000_000L + 10 * 60_000L, 36f, false)
        assertFalse(d.rapid())
    }
    @Test fun missingTemperatureClears() {
        val d = RiseDetector(); feed(d, listOf(33.0f, 33.4f, 33.9f)); d.add(2_000_000L, null, false)
        assertNull(d.ratePerMin())
    }
    @Test fun calmWhenFlatOrNoData() {
        val d = RiseDetector(); assertTrue(d.calm())
        feed(d, listOf(36.0f, 36.0f, 36.0f, 36.0f)); assertTrue(d.calm())
    }
    @Test fun policyStartsOnRapidRiseBelowLimitWhenNotCharging() {
        assertEquals(SaverDecision.APPLY, SaverPolicy.decide(true, false, 34f, 80, false, 40f, 35, rapidRise = true))
        assertEquals(SaverDecision.NONE, SaverPolicy.decide(true, false, 34f, 80, true, 40f, 35, rapidRise = true))
        assertEquals(SaverDecision.NONE, SaverPolicy.decide(true, false, 34f, 80, false, 40f, 35, rapidRise = false))
        assertEquals(SaverDecision.NONE, SaverPolicy.decide(false, false, 34f, 80, false, 40f, 35, rapidRise = true))
    }
    @Test fun riseStartStaysUntilCalmThenRestores() {
        assertEquals(SaverDecision.NONE, SaverPolicy.decide(true, true, 35f, 80, false, 40f, 35, reasonHeat = false, reasonLow = false, reasonRise = true, riseCalm = false))
        assertEquals(SaverDecision.RESTORE, SaverPolicy.decide(true, true, 35f, 80, false, 40f, 35, reasonHeat = false, reasonLow = false, reasonRise = true, riseCalm = true))
    }
    @Test fun riseStartStaysWhileNearTheLimit() {
        assertEquals(SaverDecision.NONE, SaverPolicy.decide(true, true, 39f, 80, false, 40f, 35, reasonHeat = false, reasonLow = false, reasonRise = true, riseCalm = true))
    }
    @Test fun recentPackagesListsAppsWithEventsInWindowOnly() {
        val ev = listOf(Triple("editor", 1, 100L), Triple("insta", 1, 200L), Triple("old", 2, 5L), Triple("", 1, 150L), Triple("x", 7, 160L))
        val seen = com.example.util.HeatSuspects.packagesSeen(ev, 50L, 300L)
        assertEquals(setOf("editor", "insta"), seen)
    }
}
