package com.example

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.service.CentralCapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VoltageCapabilityTruthfulnessTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dependent = listOf(CapabilityType.BATTERY_POWER_CALCULATION,
        CapabilityType.CHARGING_SPEED_CALCULATION, CapabilityType.FAST_CHARGING_DETECTION)

    @Test fun missingVoltageDoesNotClaimVoltageOrPowerAvailable() {
        val registry = CentralCapabilityRegistry(context)
        for (voltage in listOf(0, -1)) {
            val result = registry.detectAllCapabilities(currentMicroAmps = 2_000_000, voltageRaw = voltage)
            assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_CURRENT])
            assertEquals(CapabilityStatus.UNAVAILABLE, result[CapabilityType.BATTERY_VOLTAGE])
            dependent.forEach { assertEquals(CapabilityStatus.UNAVAILABLE, result[it]) }
        }
    }
    @Test fun validVoltageAndCurrentEnableCalculationCapabilities() {
        val result = CentralCapabilityRegistry(context).detectAllCapabilities(
            currentMicroAmps = 2_000_000, voltageRaw = 4000)
        assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_VOLTAGE])
        dependent.forEach { assertEquals(CapabilityStatus.AVAILABLE, result[it]) }
    }
    @Test fun validVoltageDoesNotSubstituteForMissingCurrent() {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        shadowOf(manager).setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, Int.MIN_VALUE)
        val result = CentralCapabilityRegistry(context).detectAllCapabilities(
            currentMicroAmps = Int.MIN_VALUE, voltageRaw = 4000)
        assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_VOLTAGE])
        assertEquals(CapabilityStatus.UNAVAILABLE, result[CapabilityType.BATTERY_CURRENT])
        dependent.forEach { assertEquals(CapabilityStatus.UNAVAILABLE, result[it]) }
    }
    @Test fun laterMissingVoltageRevokesCalculationAvailability() {
        val registry = CentralCapabilityRegistry(context)
        val first = registry.detectAllCapabilities(currentMicroAmps = 2_000_000, voltageRaw = 4000)
        val missing = registry.detectAllCapabilities(currentMicroAmps = 2_000_000, voltageRaw = 0)
        dependent.forEach {
            assertEquals(CapabilityStatus.AVAILABLE, first[it])
            assertEquals(CapabilityStatus.UNAVAILABLE, missing[it])
        }
    }
}
