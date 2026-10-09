package com.example

import com.example.ui.theme.RedesignGate
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedesignGateTest {
    private fun t(y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        GregorianCalendar(y, m, d, h, mi, s).timeInMillis
    @Test fun offJustBeforeSwitch() = assertFalse(RedesignGate.isOn(t(2026, Calendar.OCTOBER, 10, 23, 59, 59)))
    @Test fun onAtSwitch() = assertTrue(RedesignGate.isOn(t(2026, Calendar.OCTOBER, 11, 0, 0)))
    @Test fun onAfter() = assertTrue(RedesignGate.isOn(t(2027, Calendar.JANUARY, 1, 12, 0)))
    @Test fun offToday() = assertFalse(RedesignGate.isOn(t(2026, Calendar.OCTOBER, 9, 12, 0)))
}
