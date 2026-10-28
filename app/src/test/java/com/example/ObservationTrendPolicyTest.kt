package com.example

import com.example.ai.*
import org.junit.Assert.*
import org.junit.Test

class ObservationTrendPolicyTest {
    private val now = 100L * ObservationTrendPolicy.DAY_MS
    private fun days(range: LongRange, temp: Double, samples: Int = 10) = range.map { DailyBatteryObservation(it, samples, temp) }
    @Test fun noHistoryOrOneDenseDayDoesNotInventTrend() {
        assertNull(ObservationTrendPolicy.compare(emptyList(), now, 7).temperatureDeltaC)
        assertNull(ObservationTrendPolicy.compare(days(99L..99L, 40.0, 100000), now, 7).temperatureDeltaC)
    }
    @Test fun weeklyGateNeedsFiveDistinctCompletedDaysInBothWindows() {
        val prior = days(86L..90L, 35.0)
        assertNull(ObservationTrendPolicy.compare(prior + days(93L..96L, 37.0), now, 7).temperatureDeltaC)
        val result = ObservationTrendPolicy.compare(prior + days(93L..97L, 37.0), now, 7)
        assertEquals(2.0, result.temperatureDeltaC!!, .0001)
        assertEquals(5, result.currentDays); assertEquals(5, result.previousDays)
        assertEquals("Enough days recorded (limited)", result.coverageLabel)
    }
    @Test fun monthNeeds21Of30DaysPerWindow() {
        val prior = days(40L..60L, 38.0)
        assertNull(ObservationTrendPolicy.compare(prior + days(70L..89L, 36.0), now, 30).temperatureDeltaC)
        assertEquals(-2.0, ObservationTrendPolicy.compare(prior + days(70L..90L, 36.0), now, 30).temperatureDeltaC!!, .0001)
    }
    @Test fun futureCurrentDayInvalidAndDuplicateDaysCannotImproveCoverage() {
        val noise = days(100L..104L, 40.0) + listOf(DailyBatteryObservation(99, 10, Double.NaN), DailyBatteryObservation(98, 0, 20.0))
        val duplicated = List(200) { DailyBatteryObservation(99, 1, 37.0) }
        val r = ObservationTrendPolicy.compare(noise + duplicated, now, 7)
        assertEquals(0, r.currentDays);assertNull(r.temperatureDeltaC)
    }
    @Test fun utcRolloverExcludesTodayAndSparseDays() {
        val records = days(86L..90L,30.0) + days(93L..96L,32.0) + DailyBatteryObservation(99,5,32.0) + DailyBatteryObservation(100,10,32.0)
        assertNull(ObservationTrendPolicy.compare(records,now,7).temperatureDeltaC)
        val complete = records + DailyBatteryObservation(99,6,32.0)
        assertNotNull(ObservationTrendPolicy.compare(complete,now,7).temperatureDeltaC)
        assertNull(ObservationTrendPolicy.compare(complete,now + 2 * ObservationTrendPolicy.DAY_MS,7).temperatureDeltaC)
    }
    @Test fun dailyMeanEqualWeightNotPollingFrequencyOrCapacity() {
        val prior = days(86L..90L, 30.0)
        val current = days(93L..96L, 30.0) + DailyBatteryObservation(97, 1000000, 40.0)
        assertEquals(2.0, ObservationTrendPolicy.compare(prior + current, now, 7).temperatureDeltaC!!,.0001)
        val report = BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(emptyList(),emptyList())
        assertNull(report.estimatedCapacityHealthPercent);assertNull(report.riskPercent);assertFalse(report.requiresAlert)
    }
}
