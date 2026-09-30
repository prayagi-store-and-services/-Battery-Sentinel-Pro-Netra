package com.example

import com.example.model.AppUsageItem
import com.example.util.UsageStatsHelper
import org.junit.Assert.*
import org.junit.Test

class UsageStatsTruthfulnessTest {
    @Test fun foregroundTimeNeverBecomesEnergyOrBackgroundUsage() {
        val app = AppUsageItem("test.app", "Test", 120)
        assertNull(app.estimatedDrainPercent)
        assertNull(app.estimatedEnergyMah)
        assertNull(app.consumptionRateMahPerHour)
        assertNull(app.backgroundTimeMinutes)
        assertNull(app.isHighDrain)
        assertNull(app.anomalyWarning)
    }
    @Test fun duplicateBucketsBecomeOnePackage() {
        val result = UsageStatsHelper.aggregateForegroundTimes(listOf("a" to 60000L, "a" to 120000L, "b" to 30000L, "bad" to -1L))
        assertEquals(2, result.size)
        assertEquals(180000L, result["a"])
        assertFalse(result.containsKey("bad"))
    }
    @Test fun aggregationDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE, UsageStatsHelper.aggregateForegroundTimes(listOf("a" to Long.MAX_VALUE, "a" to 1L))["a"])
    }
}
