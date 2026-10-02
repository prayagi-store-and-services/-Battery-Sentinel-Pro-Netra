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

    /**
     * BATTERY_PROPERTY_CURRENT_NOW is specified in microamps, but some devices report milliamps.
     * While actively charging (not FULL, where trickle current is tiny), a magnitude of 2000..19999
     * is only plausible as milliamps (2-20 A); as microamps it would be a 2-20 mA trickle that
     * cannot be an actively charging phone. Everything else is treated as microamps.
     */
    fun normalizeToMilliAmps(raw: Int, activelyCharging: Boolean): Int {
        val mag = abs(raw.toLong())
        return if (activelyCharging && mag in 2000L..19999L) raw else raw / 1000
    }

    fun calculate(
        isCharging: Boolean?,
        voltageMv: Int?,
        currentMa: Int?,
        isDischarging: Boolean = false
    ): SpeedEngineResult {
        val batteryPowerWatts = if (voltageMv != null && currentMa != null) {
            (voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f
        } else null

        // Current sign conventions vary by OEM. The canonical battery status determines
        // direction; voltage × current magnitude determines the observed battery-side power.
        // This is battery-terminal power, not a claim about the charger's advertised wattage.
        val observedPowerWatts = batteryPowerWatts?.let { abs(it) }
        val rawPowerWatts = when {
            isCharging == true -> observedPowerWatts
            isDischarging -> observedPowerWatts
            else -> null
        }

        // On discharge, expose the same observed battery-side draw as consumption power.
        // Never derive a discharge value from a stale charging sample.
        val consumptionWatts = if (isDischarging) observedPowerWatts else null

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
