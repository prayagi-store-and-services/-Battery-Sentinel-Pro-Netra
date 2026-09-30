package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.service.CentralCapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TemperatureCapabilityTruthfulnessTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun omittedTemperatureIsUnavailableDespiteBatteryService() {
        val result = CentralCapabilityRegistry(context).detectAllCapabilities()
        assertEquals(CapabilityStatus.UNAVAILABLE, result[CapabilityType.BATTERY_TEMPERATURE])
    }
    @Test fun invalidTemperatureIsUnavailable() {
        val registry = CentralCapabilityRegistry(context)
        for (value in listOf(0, -1, Int.MIN_VALUE)) {
            val result = registry.detectAllCapabilities(temperatureRaw = value)
            assertEquals(CapabilityStatus.UNAVAILABLE, result[CapabilityType.BATTERY_TEMPERATURE])
        }
    }
    @Test fun observedPositiveTemperatureIsAvailable() {
        val result = CentralCapabilityRegistry(context).detectAllCapabilities(temperatureRaw = 320)
        assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_TEMPERATURE])
    }
    @Test fun laterMissingObservationRevokesTemperatureAvailability() {
        val registry = CentralCapabilityRegistry(context)
        val first = registry.detectAllCapabilities(temperatureRaw = 320)
        val later = registry.detectAllCapabilities(temperatureRaw = 0)
        assertEquals(CapabilityStatus.AVAILABLE, first[CapabilityType.BATTERY_TEMPERATURE])
        assertEquals(CapabilityStatus.UNAVAILABLE, later[CapabilityType.BATTERY_TEMPERATURE])
    }
}
