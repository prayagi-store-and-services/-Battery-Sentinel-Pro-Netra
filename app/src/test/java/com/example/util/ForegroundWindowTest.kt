package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.util.ForegroundWindow.Ev

class ForegroundWindowTest {
    private val m = 60_000L

    @Test fun simpleSession() {
        val r = ForegroundWindow.compute(listOf(Ev("a", 1, 10 * m), Ev("a", 2, 25 * m)), 0, 100 * m)
        assertEquals(15 * m, r["a"])
    }

    @Test fun openSessionEndsAtWindowEnd() {
        val r = ForegroundWindow.compute(listOf(Ev("a", 1, 90 * m)), 0, 100 * m)
        assertEquals(10 * m, r["a"])
    }

    @Test fun pauseWithoutResumeCountsFromWindowStart() {
        val r = ForegroundWindow.compute(listOf(Ev("a", 2, 5 * m)), 0, 100 * m)
        assertEquals(5 * m, r["a"])
    }

    @Test fun screenOffClosesEverySession() {
        val r = ForegroundWindow.compute(listOf(Ev("a", 1, 10 * m), Ev("", 16, 20 * m)), 0, 100 * m)
        assertEquals(10 * m, r["a"])
    }

    @Test fun eventsOutsideWindowAreClipped() {
        val r = ForegroundWindow.compute(listOf(Ev("a", 1, -50 * m), Ev("a", 2, 10 * m)), 0, 100 * m)
        assertEquals(10 * m, r["a"])
    }

    @Test fun neverExceedsTheWindow() {
        val r = ForegroundWindow.compute(listOf(Ev("a", 1, -500 * m), Ev("a", 2, 900 * m)), 0, 100 * m)
        assertEquals(100 * m, r["a"])
    }
}
