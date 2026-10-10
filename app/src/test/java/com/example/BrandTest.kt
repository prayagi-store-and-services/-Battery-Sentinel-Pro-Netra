package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class BrandTest {
    private fun at(y: Int, m: Int, d: Int, h: Int, mi: Int) = GregorianCalendar(y, m, d, h, mi, 0).timeInMillis

    @Test fun oldNameBeforeTheGate() {
        assertEquals("Battery Sentinel Pro Netra", Brand.nameAt(at(2026, Calendar.OCTOBER, 10, 23, 59)))
        assertNull(Brand.rePinNote(at(2026, Calendar.OCTOBER, 10, 23, 59)))
    }

    @Test fun newNameFromMidnight() {
        assertEquals("Netra Battery Sentinel Pro", Brand.nameAt(at(2026, Calendar.OCTOBER, 11, 0, 0)))
        assertNotNull(Brand.rePinNote(at(2026, Calendar.OCTOBER, 11, 0, 1)))
    }
}
