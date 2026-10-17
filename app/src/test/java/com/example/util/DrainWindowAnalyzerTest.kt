package com.example.util

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainWindowAnalyzerTest {

    // Times are in "minutes" scaled to milliseconds so they clear the 1-minute noise filter.
    private val MIN = 60_000L
    private fun ev(pkg: String, type: Int, tsMinutes: Long) = DrainWindowAnalyzer.TimedEvent(pkg, type, tsMinutes * MIN)

    @Test
    fun foregroundPairCountsClampedToWindow() {
        // app on screen 0..30, window 10..20 -> 10 minutes
        val events = listOf(
            ev("a", UsageEvents.Event.MOVE_TO_FOREGROUND, 0),
            ev("a", UsageEvents.Event.MOVE_TO_BACKGROUND, 30)
        )
        val r = DrainWindowAnalyzer.aggregate(events, 10 * MIN, 20 * MIN)
        assertEquals(10 * MIN, r["a"]?.foregroundMs)
    }

    @Test
    fun openIntervalAtWindowEndCountsToEnd() {
        // app enters foreground at 12 and never leaves within window 10..20
        val events = listOf(ev("a", UsageEvents.Event.MOVE_TO_FOREGROUND, 12))
        val r = DrainWindowAnalyzer.aggregate(events, 10 * MIN, 20 * MIN)
        assertEquals(8 * MIN, r["a"]?.foregroundMs)
    }

    @Test
    fun backgroundServiceMinutesCountedSeparately() {
        val events = listOf(
            ev("m", UsageEvents.Event.FOREGROUND_SERVICE_START, 10),
            ev("m", UsageEvents.Event.FOREGROUND_SERVICE_STOP, 16)
        )
        val r = DrainWindowAnalyzer.aggregate(events, 0, 20 * MIN)
        assertEquals(6 * MIN, r["m"]?.backgroundServiceMs)
        assertEquals(0L, r["m"]?.foregroundMs)
    }

    @Test
    fun activityStoppedClosesForeground() {
        val events = listOf(
            ev("a", UsageEvents.Event.MOVE_TO_FOREGROUND, 0),
            ev("a", UsageEvents.Event.ACTIVITY_STOPPED, 7)
        )
        val r = DrainWindowAnalyzer.aggregate(events, 0, 20 * MIN)
        assertEquals(7 * MIN, r["a"]?.foregroundMs)
    }

    @Test
    fun underOneMinuteDroppedAsNoise() {
        // 30 seconds of raw-ms activity is below the 1-minute noise floor
        val events = listOf(DrainWindowAnalyzer.TimedEvent("x", UsageEvents.Event.MOVE_TO_FOREGROUND, 0),
            DrainWindowAnalyzer.TimedEvent("x", UsageEvents.Event.MOVE_TO_BACKGROUND, 30_000))
        assertTrue(DrainWindowAnalyzer.aggregate(events, 0, 10 * MIN).isEmpty())
    }

    @Test
    fun emptyOrInvertedWindowIsEmpty() {
        assertTrue(DrainWindowAnalyzer.aggregate(emptyList(), 0, 100).isEmpty())
        assertTrue(DrainWindowAnalyzer.aggregate(listOf(ev("a", 1, 0)), 100 * MIN, 0).isEmpty())
    }
}
