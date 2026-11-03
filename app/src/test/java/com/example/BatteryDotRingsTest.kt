package com.example

import com.example.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class BatteryDotRingsTest {
    @Test fun slotsBoundedAndUnavailableNeverInvented() {
        assertNull(ringDots(null)); assertNull(ringDots(Float.NaN))
        assertEquals(0, ringDots(-5f)); assertEquals(100, ringDots(110f))
        for (i in 0..100) assertEquals(i, ringDots(i.toFloat()))
        assertEquals(40, ringDots(39.7f))
    }
    @Test fun temperatureRemovesOneDotPerDegreeWithoutChangingOtherRings() {
        assertNull(temperatureRingDots(null)); assertNull(temperatureRingDots(Float.NaN))
        assertNull(temperatureRingDots(Float.POSITIVE_INFINITY)); assertNull(temperatureRingDots(Float.NEGATIVE_INFINITY))
        assertEquals(100, temperatureRingDots(-5f)); assertEquals(0, temperatureRingDots(110f))
        assertEquals(100, temperatureRingDots(0f)); assertEquals(60, temperatureRingDots(39.7f))
        assertEquals(63, temperatureRingDots(37f)); assertEquals(62, temperatureRingDots(38f))
        assertEquals(0, temperatureRingDots(100f))
        for (i in 0..100) {
            assertEquals(100-i, temperatureRingDots(i.toFloat()))
            assertEquals(i, ringDots(i.toFloat()))
        }
        assertEquals(1f, ringRadialOffset(63,62,1f),0f) // warmer removes outward
        assertEquals(0f, ringRadialOffset(62,63,1f),0f) // cooler rejoins inward
    }
    @Test fun allPauseConditionsGateMotion() {
        assertTrue(ringCanAnimate(true,true,true,false,true))
        assertFalse(ringCanAnimate(false,true,true,false,true))
        assertFalse(ringCanAnimate(true,false,true,false,true))
        assertFalse(ringCanAnimate(true,true,false,false,true))
        assertFalse(ringCanAnimate(true,true,true,true,true))
        assertFalse(ringCanAnimate(true,true,true,false,false))
    }
    @Test fun addingMovesInAndRemovingMovesOut() {
        assertEquals(1f, ringRadialOffset(40,41,0f),0f)
        assertEquals(0f, ringRadialOffset(40,41,1f),0f)
        assertEquals(0f, ringRadialOffset(41,40,0f),0f)
        assertEquals(1f, ringRadialOffset(41,40,1f),0f)
    }
    @Test fun settledMetricColorThresholds() {
        assertNotEquals(ringColor(5f,false),ringColor(80f,false))
        assertNotEquals(ringColor(39.5f,true),ringColor(37f,true))
        assertNotEquals(ringColor(38f,true),ringColor(37f,true))
    }
    @Test fun healthLifePrefersReportedThenEstimatesHonestly() {
        // No readings at all: nothing invented.
        assertNull(healthLifePercent(null, null, null))
        assertNull(healthLifePercent(null, Float.NaN, null))
        // Phone-reported capacity wins and is clamped to the 0-100 ring.
        assertEquals(92f, healthLifePercent(50, 25f, 92)!!, 0f)
        assertEquals(100f, healthLifePercent(50, 25f, 118)!!, 0f)
        assertEquals(100f, healthLifePercent(null, null, 100)!!, 0f)
        // Comfortable readings keep the full estimate.
        assertEquals(100f, healthLifePercent(60, 25f, null)!!, 0f)
        // Heat removes points: 2 per degree above 30 C.
        assertEquals(80f, healthLifePercent(60, 40f, null)!!, 0f)
        assertEquals(60f, healthLifePercent(60, 50f, null)!!, 0f)
        // Deep discharge removes points: 1.5 per point below 20.
        assertEquals(85f, healthLifePercent(10, 25f, null)!!, 0f)
        // Heat and deep discharge combine.
        assertEquals(65f, healthLifePercent(10, 40f, null)!!, 0f)
        // Heat deduction caps at 45 points; cold also removes points.
        assertEquals(55f, healthLifePercent(60, 60f, null)!!, 0f)
        assertEquals(90f, healthLifePercent(60, -5f, null)!!, 0f)
        // Out-of-range readings are ignored rather than misread.
        assertNull(healthLifePercent(null, 200f, null))
        assertEquals(100f, healthLifePercent(60, 200f, null)!!, 0f)
    }
}
