package com.example

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.service.CentralCapabilityRegistry
import com.example.service.NetraCentralDataCenter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CapabilityRefreshFreshnessTest {
    private fun center(): NetraCentralDataCenter {
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, Int.MIN_VALUE)
        return NetraCentralDataCenter().also { center ->
            NetraCentralDataCenter::class.java.getDeclaredField("capabilityRegistry").apply {
                isAccessible = true
                set(center, CentralCapabilityRegistry(context))
            }
        }
    }
    private suspend fun input(center: NetraCentralDataCenter, current: Int, voltage: Int = 4000, temperature: Int = 300) {
        center.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING,
            0, temperature, voltage, current, null, null)
    }
    @Test fun retainedCurrentDoesNotBecomeFreshAtRefresh() = runTest {
        val center = center()
        input(center, 500_000)
        input(center, Int.MIN_VALUE)
        center.refreshCapabilities()
        assertEquals(500, center.centralState.value.currentMa)
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_POWER_CALCULATION])
    }
    @Test fun retainedVoltageAndTemperatureDoNotBecomeFreshAtRefresh() = runTest {
        val center = center()
        input(center, 500_000)
        input(center, 500_000, voltage = 0, temperature = 0)
        center.refreshCapabilities()
        assertEquals(4000, center.centralState.value.voltageMv)
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_VOLTAGE])
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_TEMPERATURE])
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_POWER_CALCULATION])
    }
    @Test fun liveZeroAndSensorsRemainAvailableAtRefresh() = runTest {
        val center = center()
        input(center, 0)
        center.refreshCapabilities()
        for (type in listOf(CapabilityType.BATTERY_CURRENT, CapabilityType.BATTERY_VOLTAGE,
            CapabilityType.BATTERY_TEMPERATURE, CapabilityType.BATTERY_POWER_CALCULATION)) {
            assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[type])
        }
    }
    @Test fun freshObservationRecoversAfterRetainedReading() = runTest {
        val center = center()
        input(center, 500_000)
        input(center, Int.MIN_VALUE, voltage = 0, temperature = 0)
        center.refreshCapabilities()
        input(center, -500_000)
        center.refreshCapabilities()
        for (type in listOf(CapabilityType.BATTERY_CURRENT, CapabilityType.BATTERY_VOLTAGE,
            CapabilityType.BATTERY_TEMPERATURE, CapabilityType.BATTERY_POWER_CALCULATION)) {
            assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[type])
        }
    }
}
