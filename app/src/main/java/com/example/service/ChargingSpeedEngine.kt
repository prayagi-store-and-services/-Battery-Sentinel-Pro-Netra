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

    // Learned from real readings: true when this device reports POSITIVE current while discharging
    // (inverted sign convention, common on some OEMs). null until observed.
    @Volatile
    private var invertedSignConvention: Boolean? = null

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
        currentMa: Int?
    ): SpeedEngineResult {
        // Learn the device's sign convention from genuine discharge readings.
        if (isCharging == false && currentMa != null && abs(currentMa) >= 50) {
            invertedSignConvention = currentMa > 0
        }
        // While status says CHARGING, a negative reading means this device reports charge current as
        // negative (unless it has been shown to follow the standard convention, where negative while
        // charging is a real net drain and stays unreported as incoming power).
        val chargingMa = if (isCharging == true && currentMa != null && currentMa < 0 &&
            invertedSignConvention != false) {
            -currentMa
        } else currentMa
        val batteryPowerWatts = if (voltageMv != null && chargingMa != null) {
            (voltageMv.toFloat() * chargingMa.toFloat()) / 1_000_000f
        } else null

        // Raw incoming charging power: strictly positive power delivered to battery while charging
        val rawPowerWatts = if (isCharging == true) {
            batteryPowerWatts?.coerceAtLeast(0f)
        } else if (batteryPowerWatts != null && batteryPowerWatts < 0) {
            0f
        } else {
            null
        }

        // Monitored strictly for independent phone discharge telemetry; never modifies charging speed
        val consumptionWatts = if (currentMa != null && currentMa < 0 && voltageMv != null) {
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
