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
        val batteryPowerWatts = if (voltageMv != null && currentMa != null) {
            (voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f
        } else null

        // Raw incoming charging power: strictly positive power delivered to battery while charging
        val rawPowerWatts = if (isCharging == true) {
            // CURRENT_NOW sign conventions vary across device fuel-gauge implementations.
            // Charging state is authoritative for direction; classify by current magnitude.
            batteryPowerWatts?.let { abs(it) }
        } else if (batteryPowerWatts != null && batteryPowerWatts < 0) {
            0f
        } else {
            null
        }

        // Monitored strictly for independent phone discharge telemetry; never modifies charging speed
        val consumptionWatts = if (isCharging != true && currentMa != null && currentMa < 0 && voltageMv != null) {
            abs(voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f
        } else {
            null
        }

        // Canonical raw-power tiers: <5W Slow, 5W-<10W Normal, 10W-<20W Fast, 20W-<40W Super Fast, >=40W Ultra Fast
        // Rely exclusively on raw battery input power. No 'effective' or 'net' charging power calculations.
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
