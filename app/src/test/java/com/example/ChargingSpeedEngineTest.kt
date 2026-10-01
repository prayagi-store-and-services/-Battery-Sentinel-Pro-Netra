package com.example.service

import com.example.model.CanonicalChargingSpeed
import org.junit.Test
import org.junit.Assert.*

class ChargingSpeedEngineTest {

    private val engine = ChargingSpeedEngine()

    @Test
    fun testChargingSpeedThresholds() {
        // Test boundaries
        assertEquals(CanonicalChargingSpeed.SLOW, engine.calculate(true, 4000, 1000).speedCategory) // 4W
        assertEquals(CanonicalChargingSpeed.NORMAL, engine.calculate(true, 4000, 1500).speedCategory) // 6W
        assertEquals(CanonicalChargingSpeed.FAST, engine.calculate(true, 4000, 3000).speedCategory) // 12W
        assertEquals(CanonicalChargingSpeed.SUPER_FAST, engine.calculate(true, 4000, 6000).speedCategory) // 24W
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, engine.calculate(true, 4000, 10000).speedCategory) // 40W
    }

    @Test
    fun invertedSignWhileChargingIsStillFast() {
        // Device reports charge current as negative; status says CHARGING, 4V x 3A = 12W -> FAST
        assertEquals(CanonicalChargingSpeed.FAST, ChargingSpeedEngine().calculate(true, 4000, -3000).speedCategory)
        assertEquals(CanonicalChargingSpeed.SUPER_FAST, ChargingSpeedEngine().calculate(true, 4000, -6000).speedCategory)
    }

    @Test
    fun standardSignDeviceNegativeWhileChargingStaysNetDrain() {
        val e = ChargingSpeedEngine()
        e.calculate(false, 4000, -500) // learns standard convention from discharge
        assertEquals(CanonicalChargingSpeed.SLOW, e.calculate(true, 4000, -3000).speedCategory)
    }

    @Test
    fun invertedDeviceLearnedFromPositiveDischarge() {
        val e = ChargingSpeedEngine()
        e.calculate(false, 4000, 500)
        assertEquals(CanonicalChargingSpeed.FAST, e.calculate(true, 4000, -3000).speedCategory)
    }

    @Test
    fun milliampDevicesAreNotReadAsMicroamps() {
        val e = ChargingSpeedEngine()
        assertEquals(3000, e.normalizeToMilliAmps(3000, true))        // reported in mA
        assertEquals(3000, e.normalizeToMilliAmps(3_000_000, true))   // reported in uA
        assertEquals(3, e.normalizeToMilliAmps(3000, false))          // not actively charging: uA
        assertEquals(-3000, e.normalizeToMilliAmps(-3000, true))
    }
}
