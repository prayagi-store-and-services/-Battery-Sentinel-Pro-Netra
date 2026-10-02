package com.example

import com.example.service.LivePowerReading
import org.junit.Assert.*
import org.junit.Test

class LivePowerReadingTest {
    @Test fun chargingMicroamps() {
        val r = LivePowerReading.from(4200, 3_000_000, true)
        assertEquals(3000, r.currentMa)
        assertEquals(12.6f, r.watts!!, 0.01f)
        assertEquals("12.60 W", r.wattsText())
        assertEquals("4.200 V", r.voltageText())
    }
    @Test fun dischargingNegativeMicroampsShowsMagnitude() {
        val r = LivePowerReading.from(3900, -500_000, false)
        assertEquals(1.95f, r.watts!!, 0.01f)
        assertEquals("1.95 W", r.wattsText())
    }
    @Test fun unavailableCurrentIsNotInvented() {
        val r = LivePowerReading.from(4000, Int.MIN_VALUE, true)
        assertNull(r.watts)
        assertEquals("Unavailable", r.wattsText())
        assertEquals("Unavailable", LivePowerReading.from(0, 100000, false).voltageText())
    }
}
