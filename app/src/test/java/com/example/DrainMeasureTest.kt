package com.example

import com.example.service.DrainMeasure
import com.example.service.DrainMeasure.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainMeasureTest {
    private val min = 60_000L
    @Test fun notStarted() = assertEquals(Result.NotStarted, DrainMeasure.result(-1, 0L, false, 50, 1000L, false))
    @Test fun tooShort() = assertEquals(Result.TooShort, DrainMeasure.result(80, 1000L, false, 79, 1000L + 5 * min, false))
    @Test fun chargingInvalid() = assertTrue(DrainMeasure.result(80, 1000L, true, 79, 1000L + 30 * min, false) is Result.Invalid)
    @Test fun levelUpInvalid() = assertTrue(DrainMeasure.result(60, 1000L, false, 70, 1000L + 30 * min, false) is Result.Invalid)
    @Test fun rate() {
        val r = DrainMeasure.result(80, 1000L, false, 77, 1000L + 30 * min, false) as Result.Ok
        assertEquals(3, r.dropPercent); assertEquals(30L, r.minutes); assertEquals(6f, r.percentPerHour, 0.001f)
    }
}
