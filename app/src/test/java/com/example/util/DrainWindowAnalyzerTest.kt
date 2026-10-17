package com.example.util

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainWindowAnalyzerTest {

    private fun ev(pkg: String, type: Int, ts: Long) = DrainWindowAnalyzer.TimedEvent(pkg, type, ts)

    @Test
    fun foregroundPairCountsClampedToWindow() {
        // app on screen 0..30, window 10..20 -> 10 minutes
        val events = listOf(
            ev("a", UsageEvents.Event.MOVE_TO_FOREGROUND, 0),
            ev("a", UsageEvents.Event.MOVE_TO_BACKGROUND, 30)
        )
        val r = DrainWindowAnalyzer.aggregate(events, 10, 20)
        assertEquals(10L, r["a"]?.foregroundMs)
    }

    @Test
    fun openIntervalAtWindowEndCountsToEnd() {
        // app enters foreground at 12 and never leaves within window 10..20
        val events = listOf(ev("a", UsageEvents.Event.MOVE_TO_FOREGROUND, 12))
        val r = DrainWindowAnalyzer.aggregate(events, 10, 20)
        assertEquals(8L, r["a"]?.foregroundMs)
    }

    @Test
    fun backgroundServiceMinutesCountedSeparately() {
        val events = listOf(
            ev("m", UsageEvents.Event.FOREGROUND_SERVICE_START, 10),
            ev("m", UsageEvents.Event.FOREGROUND_SERVICE_STOP, 16)
        )
        val r = DrainWindowAnalyzer.aggregate(events, 0, 20)
        assertEquals(6L, r["m"]?.backgroundServiceMs)
        assertEquals(0L, r["m"]?.foregroundMs)
    }

    @Test
    fun activityStoppedClosesForeground() {
        val events = listOf(
            ev("a", UsageEvents.Event.MOVE_TO_FOREGROUND, 0),
            ev("a", UsageEvents.Event.ACTIVITY_STOPPED, 7)
        )
        val r = DrainWindowAnalyzer.aggregate(events, 0, 20)
        assertEquals(7L, r["a"]?.foregroundMs)
    }

    @Test
    fun underOneMinuteDroppedAsNoise() {
        val events = listOf(
            ev("x", UsageEvents.Event.MOVE_TO_FOREGROUND, 0),
            ev("x", UsageEvents.Event.MOVE_TO_BACKGROUND, 30_000)
        )
        assertTrue(DrainWindowAnalyzer.aggregate(events, 0, 1_000_000).isEmpty())
    }

    @Test
    fun emptyOrInvertedWindowIsEmpty() {
        assertTrue(DrainWindowAnalyzer.aggregate(emptyList(), 0, 100).isEmpty())
        assertTrue(DrainWindowAnalyzer.aggregate(listOf(ev("a", 1, 0)), 100, 0).isEmpty())
    }
}
