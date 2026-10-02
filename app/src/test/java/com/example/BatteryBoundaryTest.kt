package com.example

import com.example.service.reachedFivePercentLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryBoundaryTest {
    @Test
    fun dropFrom75To74DoesNotAnnounce70() {
        assertNull(reachedFivePercentLevel(74, 75))
    }

    @Test
    fun announcesExactLevelWhenReached() {
        assertEquals(70, reachedFivePercentLevel(70, 71))
        assertEquals(75, reachedFivePercentLevel(75, 74))
    }

    @Test
    fun noAnnouncementWithoutBaselineOrRepeat() {
        assertNull(reachedFivePercentLevel(70, null))
        assertNull(reachedFivePercentLevel(70, 70))
    }

    @Test
    fun nonMultiplesNeverAnnounce() {
        assertNull(reachedFivePercentLevel(73, 74))
        assertNull(reachedFivePercentLevel(101, 100))
    }
}
