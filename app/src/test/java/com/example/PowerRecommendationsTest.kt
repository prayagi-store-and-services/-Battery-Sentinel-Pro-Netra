package com.example

import com.example.service.PowerRecommendations as P
import org.junit.Assert.*
import org.junit.Test

class PowerRecommendationsTest {
    private val enabled = P.levels.toSet()
    @Test fun levelsAndSkippedSamplesChooseOnlyMostUrgent() {
        assertNull(P.nextLevel(51, true, false, false, enabled, emptySet()))
        for (level in P.levels) assertEquals(level, P.nextLevel(level, true, false, false, enabled, emptySet()))
        assertEquals(5, P.nextLevel(2, true, false, false, enabled, emptySet()))
    }
    @Test fun missingStaleChargingOrUnknownNeverRecommend() {
        assertNull(P.nextLevel(null, true, false, false, enabled, emptySet()))
        assertNull(P.nextLevel(10, false, false, false, enabled, emptySet()))
        assertNull(P.nextLevel(10, true, true, true, enabled, emptySet()))
        assertNull(P.nextLevel(10, true, false, null, enabled, emptySet()))
        assertNull(P.nextLevel(10, true, null, false, enabled, emptySet()))
    }
    @Test fun disabledAndConsumedLevelsDoNotRepeat() {
        assertNull(P.nextLevel(10, true, false, false, emptySet(), emptySet()))
        assertNull(P.nextLevel(10, true, false, false, enabled, setOf(50,30,20,10)))
        assertEquals(5, P.nextLevel(5, true, false, false, enabled, setOf(50,30,20,10)))
        assertEquals(20, P.nextLevel(10, true, false, false, setOf(20), emptySet()))
    }
}
