package com.example

import com.example.util.HeatSuspects
import org.junit.Assert.*
import org.junit.Test

class HeatSuspectsTest {
    private val s = 0L
    private val e = 30 * 60_000L

    @Test fun notHotMeansNoSuspects() {
        assertFalse(HeatSuspects.isHot(39.9f))
        assertFalse(HeatSuspects.isHot(null))
        assertTrue(HeatSuspects.isHot(40f))
    }
    @Test fun simpleForegroundSpan() {
        val m = HeatSuspects.foregroundMs(listOf(Triple("a", 1, 60_000L), Triple("a", 2, 360_000L)), s, e)
        assertEquals(300_000L, m["a"])
    }
    @Test fun stillOpenAtEndCountsToEnd() {
        val m = HeatSuspects.foregroundMs(listOf(Triple("a", 1, e - 120_000L)), s, e)
        assertEquals(120_000L, m["a"])
    }
    @Test fun backgroundFirstMeansOpenSinceWindowStart() {
        val m = HeatSuspects.foregroundMs(listOf(Triple("a", 2, 90_000L)), s, e)
        assertEquals(90_000L, m["a"])
    }
    @Test fun eventsOutsideWindowIgnoredAndSpansAdd() {
        val m = HeatSuspects.foregroundMs(listOf(
            Triple("a", 1, 0L), Triple("a", 2, 60_000L),
            Triple("a", 1, 120_000L), Triple("a", 2, 240_000L),
            Triple("b", 1, e + 5L)), s, e)
        assertEquals(180_000L, m["a"])
        assertFalse(m.containsKey("b"))
    }
    @Test fun rankDropsShortAndOwnAppAndLimitsToThree() {
        val r = HeatSuspects.rank(mapOf("me" to 900_000L, "a" to 600_000L, "b" to 59_000L, "c" to 120_000L, "d" to 300_000L, "e" to 200_000L), setOf("me"))
        assertEquals(listOf("a", "d", "e"), r.map { it.first })
    }
}
