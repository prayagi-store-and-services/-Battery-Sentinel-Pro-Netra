package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PercentAnnouncementTest {

    @Test
    fun tenPercentStepsStartAt20() {
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80, 90, 100), PercentAnnouncement.steps(10))
    }

    @Test
    fun fivePercentStepsStartAt5() {
        assertEquals((5..100 step 5).toList(), PercentAnnouncement.steps(5))
    }

    @Test
    fun unknownStepIsEmpty() {
        assertEquals(emptyList<Int>(), PercentAnnouncement.steps(15))
    }

    @Test
    fun chargingCrossingUpwardAnnouncesHighestCrossedMark() {
        assertEquals(40, PercentAnnouncement.crossed(38, 42, charging = true, step = 5))
        assertEquals(100, PercentAnnouncement.crossed(88, 100, charging = true, step = 10))
    }

    @Test
    fun dischargingCrossingDownwardAnnouncesLowestCrossedMark() {
        assertEquals(40, PercentAnnouncement.crossed(42, 38, charging = false, step = 5))
        assertEquals(30, PercentAnnouncement.crossed(34, 28, charging = false, step = 10))
    }

    @Test
    fun wrongDirectionCrossesNothing() {
        assertNull(PercentAnnouncement.crossed(38, 42, charging = false, step = 5))
        assertNull(PercentAnnouncement.crossed(42, 38, charging = true, step = 10))
    }

    @Test
    fun noBoundaryCrossedIsNull() {
        assertNull(PercentAnnouncement.crossed(41, 42, charging = true, step = 5))
        assertNull(PercentAnnouncement.crossed(25, 24, charging = false, step = 10))
    }

    @Test
    fun nullOrUnchangedLevelsAreNull() {
        assertNull(PercentAnnouncement.crossed(null, 42, charging = true, step = 5))
        assertNull(PercentAnnouncement.crossed(38, null, charging = false, step = 5))
        assertNull(PercentAnnouncement.crossed(40, 40, charging = true, step = 5))
    }

    @Test
    fun dischargeFrom100With5PercentStepAnnounces95Not100() {
        // Level 100 was the starting point, not a crossed boundary.
        assertEquals(95, PercentAnnouncement.crossed(100, 94, charging = false, step = 5))
    }
}
