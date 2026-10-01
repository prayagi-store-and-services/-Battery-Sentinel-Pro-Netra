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
}
