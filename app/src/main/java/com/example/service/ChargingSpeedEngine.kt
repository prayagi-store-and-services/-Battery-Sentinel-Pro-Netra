package com.example.service

import com.example.model.CanonicalChargingSpeed
import kotlin.math.abs

data class SpeedEngineResult(
    val rawPowerWatts: Float?,
    val consumptionPowerWatts: Float?,
    val speedCategory: CanonicalChargingSpeed,
    val announcementCategory: CanonicalChargingSpeed,
    val estimatedStoredEnergyRateWatts: Float? = null
)

class ChargingSpeedEngine {
    private val energyPolicy = EnergyCounterSpeedPolicy()

    companion object {
        /**
         * BATTERY_PROPERTY_CURRENT_NOW is specified in microamps, but some devices report milliamps.
         * During active charging or discharging, a magnitude of 2000..19999 is treated as
         * milliamps (2-20 A); as microamps it would be a 2-20 mA trickle. This is an OEM heuristic,
         * not a guaranteed unit detector. Everything else is treated as microamps.
         */
        fun normalizeToMilliAmps(raw: Int, activeCurrentFlow: Boolean): Int {
            val mag = abs(raw.toLong())
            return if (activeCurrentFlow && mag in 2000L..19999L) raw else raw / 1000
        }
    }

    fun normalizeToMilliAmps(raw: Int, activeCurrentFlow: Boolean): Int =
        ChargingSpeedEngine.normalizeToMilliAmps(raw, activeCurrentFlow)

    @Synchronized
    fun calculate(
        isCharging: Boolean?,
        voltageMv: Int?,
        currentMa: Int?,
        isDischarging: Boolean = false,
        energyCounterNwh: Long? = null,
        observedAtElapsedMillis: Long? = null,
        isFull: Boolean = false
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

        val energyReading = energyPolicy.observe(isCharging == true && !isFull,
            energyCounterNwh, observedAtElapsedMillis)
        val speedCategory = when (energyReading.speed) {
            EnergySpeed.SLOW -> CanonicalChargingSpeed.SLOW
            EnergySpeed.NORMAL -> CanonicalChargingSpeed.NORMAL
            EnergySpeed.FAST -> CanonicalChargingSpeed.FAST
            EnergySpeed.ULTRA_FAST -> CanonicalChargingSpeed.ULTRA_FAST
            EnergySpeed.HYPER_FAST -> CanonicalChargingSpeed.HYPER_FAST
            EnergySpeed.EXTREME_FAST -> CanonicalChargingSpeed.EXTREME_FAST
            null -> CanonicalChargingSpeed.UNAVAILABLE
        }

        return SpeedEngineResult(
            rawPowerWatts = rawPowerWatts,
            consumptionPowerWatts = consumptionWatts,
            speedCategory = speedCategory,
            announcementCategory = speedCategory,
            estimatedStoredEnergyRateWatts = energyReading.watts?.toFloat()
        )
    }
}
