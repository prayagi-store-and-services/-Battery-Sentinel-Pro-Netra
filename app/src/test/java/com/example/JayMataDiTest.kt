package com.example

import com.example.festival.JayMataDi
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JayMataDiTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = GregorianCalendar(y, m, d, h, 0, 0).timeInMillis
    @Test fun notBeforeTheGate() = assertFalse(JayMataDi.due(at(2026, Calendar.OCTOBER, 10, 23), false))
    @Test fun greetsOnFirstOpenOnTheDay() = assertTrue(JayMataDi.due(at(2026, Calendar.OCTOBER, 11, 0), false))
    @Test fun onlyOnce() = assertFalse(JayMataDi.due(at(2026, Calendar.OCTOBER, 12), true))
    @Test fun stillGreetsLateInTheWindow() = assertTrue(JayMataDi.due(at(2026, Calendar.OCTOBER, 20, 22), false))
    @Test fun neverAfterTheWindow() = assertFalse(JayMataDi.due(at(2026, Calendar.OCTOBER, 21, 0), false))
}
