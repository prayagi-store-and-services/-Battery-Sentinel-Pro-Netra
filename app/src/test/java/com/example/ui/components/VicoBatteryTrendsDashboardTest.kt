package com.example.ui.components

import com.example.data.local.BatteryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VicoBatteryTrendsDashboardTest {

    @Test
    fun recordsWithin24Hours_filterCorrectly() {
        val now = System.currentTimeMillis()
        val within24h = now - (6 * 3600_000L) // 6 hours ago
        val olderThan24h = now - (30 * 3600_000L) // 30 hours ago

        val records = listOf(
            BatteryRecord(id = 1, timestamp = olderThan24h, level = 20, temperature = 25f, voltageMv = 3800, currentMa = 0, powerWatts = 0f, isCharging = false, pluggedType = "BATTERY", healthStatus = "GOOD"),
            BatteryRecord(id = 2, timestamp = within24h, level = 85, temperature = 32.5f, voltageMv = 4200, currentMa = 1200, powerWatts = 5f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD"),
            BatteryRecord(id = 3, timestamp = now, level = 90, temperature = 34.0f, voltageMv = 4250, currentMa = 800, powerWatts = 3.4f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD")
        )

        val windowStart = now - (24 * 3600_000L)
        val filtered = records.filter { it.timestamp >= windowStart }

        assertEquals(2, filtered.size)
        assertEquals(85, filtered.first().level)
        assertEquals(90, filtered.last().level)
    }

    @Test
    fun statisticsCalculations_levelAndTemperature_areAccurate() {
        val now = System.currentTimeMillis()
        val records = listOf(
            BatteryRecord(id = 1, timestamp = now - 5000, level = 40, temperature = 28.0f, voltageMv = 3800, currentMa = 0, powerWatts = 0f, isCharging = false, pluggedType = "BATTERY", healthStatus = "GOOD"),
            BatteryRecord(id = 2, timestamp = now - 3000, level = 60, temperature = 35.0f, voltageMv = 4000, currentMa = 1000, powerWatts = 4f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD"),
            BatteryRecord(id = 3, timestamp = now - 1000, level = 80, temperature = 42.0f, voltageMv = 4200, currentMa = 1500, powerWatts = 6.3f, isCharging = true, pluggedType = "AC", healthStatus = "GOOD")
        )

        val levels = records.map { it.level }
        val temps = records.map { it.temperature }

        val minLevel = levels.minOrNull() ?: 0
        val maxLevel = levels.maxOrNull() ?: 0
        val avgLevel = levels.average().toInt()
        val netChange = levels.last() - levels.first()

        assertEquals(40, minLevel)
        assertEquals(80, maxLevel)
        assertEquals(60, avgLevel)
        assertEquals(40, netChange)

        val minTemp = temps.minOrNull() ?: 0f
        val maxTemp = temps.maxOrNull() ?: 0f
        val avgTemp = temps.average().toFloat()

        assertEquals(28.0f, minTemp, 0.01f)
        assertEquals(42.0f, maxTemp, 0.01f)
        assertEquals(35.0f, avgTemp, 0.01f)
        assertTrue("Max temperature should flag overheat alert (>40°C)", maxTemp >= 40.0f)
    }

    @Test
    fun trendMetricEnum_containsRequiredMetrics() {
        val metrics = VicoTrendMetric.values()
        assertEquals(3, metrics.size)
        assertTrue(metrics.contains(VicoTrendMetric.PERCENTAGE))
        assertTrue(metrics.contains(VicoTrendMetric.TEMPERATURE))
        assertTrue(metrics.contains(VicoTrendMetric.COMBINED))
    }
}
