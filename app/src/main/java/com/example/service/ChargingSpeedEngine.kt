package com.example.service

import com.example.model.CanonicalChargingSpeed
import kotlin.math.abs

data class SpeedEngineResult(
    val rawPowerWatts: Float?,
    val consumptionPowerWatts: Float?,
    val speedCategory: CanonicalChargingSpeed,
    val announcementCategory: CanonicalChargingSpeed
)

class ChargingSpeedEngine {

    fun calculate(
        isCharging: Boolean?,
        voltageMv: Int?,
        currentMa: Int?
    ): SpeedEngineResult {
        // CURRENT_NOW sign conventions can differ across device implementations.
        // When Android confirms the battery is charging, use current magnitude for
        // battery-side power so a negative charging-current sample is not clamped to 0W.
        val powerCurrentMa = if (isCharging == true && currentMa != null) {
            abs(currentMa.toFloat())
        } else {
            currentMa?.toFloat()
        }

        val batteryPowerWatts = if (voltageMv != null && powerCurrentMa != null) {
            (voltageMv.toFloat() * powerCurrentMa) / 1_000_000f
        } else null

        // Raw battery-side charging power: do not subtract phone consumption.
        val rawPowerWatts = if (isCharging == true) {
            batteryPowerWatts?.coerceAtLeast(0f)
        } else if (batteryPowerWatts != null && batteryPowerWatts < 0) {
            0f
        } else {
            null
        }

        // Negative current is treated as discharge consumption only when the battery
        // is not confirmed to be charging; negative charging polarity is not consumption.
        val consumptionWatts = if (
            isCharging != true && currentMa != null && currentMa < 0 && voltageMv != null
        ) {
            abs(voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f
        } else {
            null
        }

        // Canonical raw-power tiers: <5W Slow, 5W-<10W Normal, 10W-<20W Fast,
        // 20W-<40W Super Fast, >=40W Ultra Fast.
        // These are based on available battery-side V×I telemetry, not guaranteed
        // external charger-input power. No effective/net-power subtraction is applied.
        val speedCategory = if (isCharging == true && rawPowerWatts != null) {
            when {
                rawPowerWatts >= 40.0f -> CanonicalChargingSpeed.ULTRA_FAST
                rawPowerWatts >= 20.0f -> CanonicalChargingSpeed.SUPER_FAST
                rawPowerWatts >= 10.0f -> CanonicalChargingSpeed.FAST
                rawPowerWatts >= 5.0f -> CanonicalChargingSpeed.NORMAL
                else -> CanonicalChargingSpeed.SLOW
            }
        } else {
            CanonicalChargingSpeed.UNAVAILABLE
        }

        return SpeedEngineResult(
            rawPowerWatts = rawPowerWatts,
            consumptionPowerWatts = consumptionWatts,
            speedCategory = speedCategory,
            announcementCategory = speedCategory
        )
    }
}
