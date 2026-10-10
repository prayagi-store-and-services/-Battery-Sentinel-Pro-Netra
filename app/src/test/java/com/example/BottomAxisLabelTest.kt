package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomAxisLabelTest {
    private val labels = (0 until 25).map { "t$it" }

    @Test fun neverEmptyForAnyValue() {
        for (v in listOf(-5.0, -0.4, 0.0, 0.5, 3.0, 10.0, 24.0, 25.0, 99.9, Double.NaN, Double.MAX_VALUE))
            assertTrue("value $v", bottomAxisLabel(labels, v).isNotEmpty())
        assertTrue(bottomAxisLabel(emptyList(), 0.0).isNotEmpty())
        assertTrue(bottomAxisLabel(listOf(""), 0.0).isNotEmpty())
    }

    @Test fun showsFirstLastAndEveryTenth() {
        assertEquals("t0", bottomAxisLabel(labels, 0.0))
        assertEquals("t10", bottomAxisLabel(labels, 10.0))
        assertEquals("t20", bottomAxisLabel(labels, 20.0))
        assertEquals("t24", bottomAxisLabel(labels, 24.0))
        assertEquals(" ", bottomAxisLabel(labels, 7.0))
    }
}
