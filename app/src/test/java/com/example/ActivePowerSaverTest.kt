package com.example

import com.example.service.ActivePowerSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivePowerSaverTest {
    @Test fun windowIsEleven_toSeven() {
        assertTrue(ActivePowerSaver.inForcedWindow(23))
        assertTrue(ActivePowerSaver.inForcedWindow(0))
        assertTrue(ActivePowerSaver.inForcedWindow(6))
        assertFalse(ActivePowerSaver.inForcedWindow(7))
        assertFalse(ActivePowerSaver.inForcedWindow(22))
        assertFalse(ActivePowerSaver.inForcedWindow(12))
    }

    @Test fun forcedWindowIgnoresActivity() {
        assertTrue(ActivePowerSaver.shouldBeActive(true, 2, true, 0))
        assertFalse(ActivePowerSaver.shouldBeActive(false, 2, false, 99_999_999))
    }

    @Test fun outsideWindowNeedsScreenOffForFifteenMinutes() {
        assertFalse(ActivePowerSaver.shouldBeActive(true, 12, false, 14 * 60_000L))
        assertTrue(ActivePowerSaver.shouldBeActive(true, 12, false, 15 * 60_000L))
        assertFalse(ActivePowerSaver.shouldBeActive(true, 12, true, 20 * 60_000L))
        assertEquals(15 * 60_000L, ActivePowerSaver.IDLE_MS)
    }
}
