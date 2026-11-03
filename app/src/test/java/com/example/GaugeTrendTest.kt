package com.example

import com.example.ui.components.GaugeTrend
import com.example.ui.components.TrendTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class GaugeTrendTest {
    @Test fun nullAndFlatStayFlat() {
        val t = TrendTracker(0.3f)
        assertEquals(GaugeTrend.FLAT, t.update(null))
        assertEquals(GaugeTrend.FLAT, t.update(30f))
        assertEquals(GaugeTrend.FLAT, t.update(30.1f))
        assertEquals(GaugeTrend.FLAT, t.update(30.2f))
    }
    @Test fun crossingDeadbandRises() {
        val t = TrendTracker(0.3f)
        t.update(30f)
        assertEquals(GaugeTrend.RISING, t.update(30.4f))
    }
    @Test fun risingHoldsUntilReversalPastDeadband() {
        val t = TrendTracker(0.3f)
        t.update(30f); t.update(30.5f)
        assertEquals(GaugeTrend.RISING, t.update(30.4f))
        assertEquals(GaugeTrend.RISING, t.update(30.3f))
        assertEquals(GaugeTrend.FLAT, t.update(30.1f))
    }
    @Test fun fallingSymmetric() {
        val t = TrendTracker(0.3f)
        t.update(30f); assertEquals(GaugeTrend.FALLING, t.update(29.6f))
        assertEquals(GaugeTrend.FALLING, t.update(29.7f))
        assertEquals(GaugeTrend.FLAT, t.update(30.0f))
    }
    @Test fun nonFiniteResets() {
        val t = TrendTracker(0.3f)
        t.update(30f); t.update(30.5f)
        assertEquals(GaugeTrend.FLAT, t.update(Float.NaN))
        assertEquals(GaugeTrend.FLAT, t.current())
    }
}
