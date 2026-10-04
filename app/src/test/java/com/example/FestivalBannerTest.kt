package com.example

import com.example.festival.BannerKind
import com.example.festival.Condolence
import com.example.festival.FestivalBanner
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalBannerTest {
    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }

    @Test fun indiaIndependenceDayShowsFirstInTheList() {
        val b = FestivalBanner.pick(day(2026, 8, 15))
        assertNotNull(b)
        assertEquals(BannerKind.FEST, b!!.kind)
        assertTrue(b.text.contains("Independence Day: India"))
    }

    @Test fun diwaliShowsOnItsDay() {
        val b = FestivalBanner.pick(day(2026, 11, 8))
        assertTrue(b!!.text.contains("Diwali"))
    }

    @Test fun quietDayAfterTheDataEndsShowsNothing() = assertNull(FestivalBanner.pick(day(2028, 3, 3)))

    @Test fun condolenceOverridesEverythingWhileActive() {
        val c = listOf(Condolence(20260815, 20260816, "X", "Y", "t"))
        val b = FestivalBanner.pick(day(2026, 8, 15), c)
        assertEquals(BannerKind.SORROW, b!!.kind)
        assertEquals(BannerKind.FEST, FestivalBanner.pick(day(2026, 8, 17), c)!!.kind)
    }

    @Test fun noDeathAnniversariesOrMourningObservancesInTheData() {
        val bad = Regex("martyr|ashura|muharram|death|anniversary", RegexOption.IGNORE_CASE)
        assertTrue(com.example.festival.FestivalData.FEST.none { bad.containsMatchIn(it.name) })
    }

    @Test fun onlyIndiasIndependenceDayIsInTheData() {
        val list = com.example.festival.FestivalData.INDEP
        assertEquals(1, list.size)
        assertEquals("India", list[0].country)
    }

    @Test fun otherCountriesIndependenceDaysShowNothing() {
        // 4 Jul (USA), 14 Aug (Pakistan), 26 Mar (Bangladesh), 12 Dec (Kenya): no independence banner, no festival that day.
        for ((m, d) in listOf(7 to 4, 8 to 14, 3 to 26, 12 to 12)) {
            val b = FestivalBanner.pick(day(2028, m, d))
            assertNull("2028-$m-$d", b)
        }
    }
}
