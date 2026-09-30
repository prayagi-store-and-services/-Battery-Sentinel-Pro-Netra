package com.example.service

import com.example.model.AdaptiveThermalContext
import com.example.model.AdaptiveWarningCategory
import com.example.model.ClimateProfile
import com.example.model.DeviceIdleState
import com.example.model.TemperatureDeviation

object ClimateBaselineEngine {

    fun computeAdaptiveThermalContext(
        countryCode: String?,
        ambientTempCelsius: Float?,
        actualBatteryTempCelsius: Float?,
        deviceIdleState: DeviceIdleState = DeviceIdleState.IDLE,
        isCharging: Boolean? = false
    ): AdaptiveThermalContext {
        // 1. Determine Climate Profile & Expected Idle Battery Range
        val (profile, profileLabel, minExpected, maxExpected, confidence) = when {
            countryCode.equals("IN", ignoreCase = true) -> {
                when {
                    ambientTempCelsius != null && ambientTempCelsius in 20.0f..25.5f -> {
                        Tuple5(
                            ClimateProfile.INDIA_WINTER_AC,
                            "India Winter / AC Room",
                            26.0f,
                            32.0f,
                            0.95f
                        )
                    }
                    ambientTempCelsius != null && ambientTempCelsius > 25.5f && ambientTempCelsius <= 35.0f -> {
                        Tuple5(
                            ClimateProfile.INDIA_MONSOON_MODERATE,
                            "India Monsoon / Moderate",
                            31.0f,
                            35.0f,
                            0.95f
                        )
                    }
                    ambientTempCelsius != null && ambientTempCelsius > 35.0f -> {
                        Tuple5(
                            ClimateProfile.INDIA_PEAK_SUMMER,
                            "India Peak Summer",
                            36.0f,
                            41.0f,
                            0.95f
                        )
                    }
                    ambientTempCelsius != null && ambientTempCelsius < 20.0f -> {
                        Tuple5(
                            ClimateProfile.INDIA_WINTER_AC,
                            "India Cool Climate",
                            (ambientTempCelsius + 4f).coerceAtLeast(20f),
                            (ambientTempCelsius + 8f).coerceAtLeast(26f),
                            0.90f
                        )
                    }
                    else -> {
                        // India without ambient weather data
                        Tuple5(
                            ClimateProfile.INDIA_MONSOON_MODERATE,
                            "India Standard Reference",
                            30.0f,
                            36.0f,
                            0.60f
                        )
                    }
                }
            }
            ambientTempCelsius != null -> {
                // International / Generic model based on actual ambient temperature
                val minExp = (ambientTempCelsius + 3.0f).coerceAtLeast(20.0f)
                val maxExp = (ambientTempCelsius + 7.0f).coerceAtLeast(25.0f)
                val prof = when {
                    ambientTempCelsius >= 35.0f -> ClimateProfile.GENERIC_TROPICAL
                    ambientTempCelsius <= 10.0f -> ClimateProfile.GENERIC_COLD
                    else -> ClimateProfile.GENERIC_CONTINENTAL
                }
                val label = when (prof) {
                    ClimateProfile.GENERIC_TROPICAL -> "Tropical / Hot Climate"
                    ClimateProfile.GENERIC_COLD -> "Cold / Nordic Climate"
                    else -> "Continental / Moderate Climate"
                }
                Tuple5(prof, label, minExp, maxExp, 0.85f)
            }
            else -> {
                // Unknown country & no weather data
                Tuple5(
                    ClimateProfile.INSUFFICIENT_DATA,
                    "Generic Baseline (No Weather Data)",
                    28.0f,
                    35.0f,
                    0.30f
                )
            }
        }

        // 2. Compute Temperature Deviation
        val deviation = when {
            actualBatteryTempCelsius == null -> {
                TemperatureDeviation.INSUFFICIENT_CONTEXT
            }
            actualBatteryTempCelsius in minExpected..maxExpected -> {
                TemperatureDeviation.WITHIN_BASELINE
            }
            actualBatteryTempCelsius > maxExpected && actualBatteryTempCelsius <= maxExpected + 4.0f -> {
                TemperatureDeviation.ABOVE_BASELINE
            }
            actualBatteryTempCelsius > maxExpected + 4.0f -> {
                TemperatureDeviation.STRONGLY_ABOVE_BASELINE
            }
            else -> {
                TemperatureDeviation.BELOW_BASELINE
            }
        }

        // 3. Warning Category & Contextual Attribution
        var isHotWeatherAttributed = false
        val warningCategory: AdaptiveWarningCategory
        val diagnosticMessage: String

        when {
            // Safety threshold remains 100% authoritative!
            actualBatteryTempCelsius != null && actualBatteryTempCelsius >= 40.0f -> {
                warningCategory = AdaptiveWarningCategory.CRITICAL_THERMAL
                diagnosticMessage = "Critical thermal threshold reached (${String.format("%.1f", actualBatteryTempCelsius)}°C). Safety control active."
            }
            // Ambient is high (e.g. India Summer >38°C) and battery temp is within expected hot weather baseline
            ambientTempCelsius != null && ambientTempCelsius >= 38.0f &&
                actualBatteryTempCelsius != null && actualBatteryTempCelsius <= (maxExpected ?: 41.0f) -> {
                warningCategory = AdaptiveWarningCategory.CLIMATE_ELEVATED
                isHotWeatherAttributed = true
                diagnosticMessage = "High ambient temperature (${String.format("%.1f", ambientTempCelsius)}°C) detected. Device temperature (${String.format("%.1f", actualBatteryTempCelsius)}°C) is elevated but consistent with current environmental conditions."
            }
            // Thermal anomaly: Low/moderate ambient but battery is strongly above baseline while idle
            deviation == TemperatureDeviation.STRONGLY_ABOVE_BASELINE && deviceIdleState != DeviceIdleState.ACTIVE -> {
                warningCategory = AdaptiveWarningCategory.THERMAL_ANOMALY
                val ambStr = ambientTempCelsius?.let { "${String.format("%.1f", it)}°C ambient" } ?: "ambient baseline"
                diagnosticMessage = "Device temperature (${String.format("%.1f", actualBatteryTempCelsius)}°C) is significantly above current environmental baseline (${minExpected?.toInt()}–${maxExpected?.toInt()}°C at $ambStr)."
            }
            deviation == TemperatureDeviation.WITHIN_BASELINE -> {
                warningCategory = AdaptiveWarningCategory.NORMAL_IDLE
                diagnosticMessage = "Temperature normal for current environment (${minExpected?.toInt()}–${maxExpected?.toInt()}°C)."
            }
            actualBatteryTempCelsius != null -> {
                warningCategory = AdaptiveWarningCategory.NORMAL_IDLE
                diagnosticMessage = "Device thermal state operating near baseline (${minExpected?.toInt()}–${maxExpected?.toInt()}°C)."
            }
            else -> {
                warningCategory = AdaptiveWarningCategory.INSUFFICIENT_CONTEXT
                diagnosticMessage = "Environmental thermal baseline calculating..."
            }
        }

        return AdaptiveThermalContext(
            climateProfile = profile,
            climateProfileLabel = profileLabel,
            expectedIdleBatteryMin = minExpected,
            expectedIdleBatteryMax = maxExpected,
            temperatureDeviation = deviation,
            warningCategory = warningCategory,
            diagnosticMessage = diagnosticMessage,
            environmentalConfidence = confidence,
            isHotWeatherAttributed = isHotWeatherAttributed
        )
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
