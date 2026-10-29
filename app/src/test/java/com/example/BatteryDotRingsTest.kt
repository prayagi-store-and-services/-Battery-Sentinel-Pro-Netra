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
}
