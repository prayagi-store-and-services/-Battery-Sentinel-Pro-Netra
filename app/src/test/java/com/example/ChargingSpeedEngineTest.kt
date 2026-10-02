package com.example.service

import com.example.model.CanonicalChargingSpeed
import org.junit.Test
import org.junit.Assert.*

class ChargingSpeedEngineTest {

    private val engine = ChargingSpeedEngine()

    @Test
    fun testChargingSpeedThresholds() {
        // Positive CURRENT_NOW sign convention.
        assertEquals(CanonicalChargingSpeed.SLOW, engine.calculate(true, 4000, 1000).speedCategory) // 4W
        assertEquals(CanonicalChargingSpeed.NORMAL, engine.calculate(true, 4000, 1500).speedCategory) // 6W
        assertEquals(CanonicalChargingSpeed.FAST, engine.calculate(true, 4000, 3000).speedCategory) // 12W
        assertEquals(CanonicalChargingSpeed.SUPER_FAST, engine.calculate(true, 4000, 6000).speedCategory) // 24W
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, engine.calculate(true, 4000, 10000).speedCategory) // 40W
    }

    @Test
    fun chargingSpeedUsesCurrentMagnitudeWhenDeviceReportsNegativeChargingCurrent() {
        // Some device fuel-gauge implementations report charging current with a negative sign.
        // When Android confirms charging, the sign must not collapse the calculated power to 0W.
        assertEquals(CanonicalChargingSpeed.SLOW, engine.calculate(true, 4000, -1000).speedCategory) // 4W
        assertEquals(CanonicalChargingSpeed.NORMAL, engine.calculate(true, 4000, -1500).speedCategory) // 6W
        assertEquals(CanonicalChargingSpeed.FAST, engine.calculate(true, 4000, -3000).speedCategory) // 12W
        assertEquals(CanonicalChargingSpeed.SUPER_FAST, engine.calculate(true, 4000, -6000).speedCategory) // 24W
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, engine.calculate(true, 4000, -10000).speedCategory) // 40W
    }

    @Test
    fun negativeCurrentDuringConfirmedChargingIsNotMisreportedAsPhoneConsumption() {
        val result = engine.calculate(true, 4000, -3000)
        assertEquals(12.0f, result.rawPowerWatts!!, 0.001f)
        assertNull(result.consumptionPowerWatts)
    }

    @Test
    fun dischargePowerIsLivePositiveMagnitudeRegardlessOfOemCurrentSign() {
        val negativeCurrent = engine.calculate(
            isCharging = false,
            voltageMv = 4_000,
            currentMa = -750,
            isDischarging = true
        )
        assertEquals(3.0f, negativeCurrent.rawPowerWatts!!, 0.001f)
        assertEquals(3.0f, negativeCurrent.consumptionPowerWatts!!, 0.001f)
        assertEquals(CanonicalChargingSpeed.UNAVAILABLE, negativeCurrent.speedCategory)

        val positiveCurrent = engine.calculate(
            isCharging = false,
            voltageMv = 4_000,
            currentMa = 750,
            isDischarging = true
        )
        assertEquals(3.0f, positiveCurrent.rawPowerWatts!!, 0.001f)
        assertEquals(3.0f, positiveCurrent.consumptionPowerWatts!!, 0.001f)
    }

    @Test
    fun milliampDevicesAreNotReadAsMicroamps() {
        assertEquals(3000, engine.normalizeToMilliAmps(3000, true))        // reported in mA
        assertEquals(3000, engine.normalizeToMilliAmps(3_000_000, true))   // reported in uA
        assertEquals(3, engine.normalizeToMilliAmps(3000, false))          // not actively charging: uA
        assertEquals(-3000, engine.normalizeToMilliAmps(-3000, true))
        assertEquals(CanonicalChargingSpeed.FAST, engine.calculate(true, 4000, engine.normalizeToMilliAmps(-3000, true)).speedCategory)
    }
}
