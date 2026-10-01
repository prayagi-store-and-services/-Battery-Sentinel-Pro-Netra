package com.example

import com.example.model.AdaptiveWarningCategory
import com.example.model.TemperatureDeviation
import com.example.service.ClimateBaselineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClimateFiniteTelemetryTest {
    private val invalid = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

    @Test fun nonfiniteBatteryIsInsufficientNotNormalOrCritical() {
        for (value in invalid) {
            val result = ClimateBaselineEngine.computeAdaptiveThermalContext("IN", 42f, value)
            assertEquals(TemperatureDeviation.INSUFFICIENT_CONTEXT, result.temperatureDeviation)
            assertEquals(AdaptiveWarningCategory.INSUFFICIENT_CONTEXT, result.warningCategory)
            assertFalse(result.isHotWeatherAttributed)
        }
    }

    @Test fun nonfiniteAmbientUsesMissingAmbientFallbackInEveryCountry() {
        for (country in listOf("IN", "US", null)) {
            val missing = ClimateBaselineEngine.computeAdaptiveThermalContext(country, null, 33f)
            for (value in invalid) {
                assertEquals(missing, ClimateBaselineEngine.computeAdaptiveThermalContext(country, value, 33f))
            }
        }
    }

    @Test fun validBatterySafetyThresholdRemainsAuthoritativeWithoutAmbient() {
        for (value in invalid) {
            assertEquals(AdaptiveWarningCategory.CRITICAL_THERMAL,
                ClimateBaselineEngine.computeAdaptiveThermalContext("IN", value, 42f).warningCategory)
        }
    }

    @Test fun finiteWeatherAndBatteryStillUseExistingBaseline() {
        val result = ClimateBaselineEngine.computeAdaptiveThermalContext("IN", 23f, 30f)
        assertEquals(TemperatureDeviation.WITHIN_BASELINE, result.temperatureDeviation)
        assertEquals(AdaptiveWarningCategory.NORMAL_IDLE, result.warningCategory)
        assertEquals(26f, result.expectedIdleBatteryMin)
        assertEquals(32f, result.expectedIdleBatteryMax)
    }
}
