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
class TelemetryCapabilityTransitionTest {
    private fun center(): NetraCentralDataCenter {
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, Int.MIN_VALUE)
        return NetraCentralDataCenter().also { center ->
            NetraCentralDataCenter::class.java.getDeclaredField("capabilityRegistry").apply {
                isAccessible = true
                set(center, CentralCapabilityRegistry(context))
            }
            center.refreshCapabilities()
        }
    }
    private suspend fun input(center: NetraCentralDataCenter, current: Int, voltage: Int = 4000, temperature: Int = 300) {
        center.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING,
            0, temperature, voltage, current, null, null)
    }
    private fun assertPower(center: NetraCentralDataCenter, status: CapabilityStatus) {
        for (type in listOf(CapabilityType.BATTERY_POWER_CALCULATION,
            CapabilityType.CHARGING_SPEED_CALCULATION, CapabilityType.FAST_CHARGING_DETECTION)) {
            assertEquals(status, center.centralState.value.capabilities[type])
        }
    }
    @Test fun zeroCurrentPromotesCapabilityInFastPath() = runTest {
        val center = center()
        input(center, 0)
        assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
        assertPower(center, CapabilityStatus.AVAILABLE)
    }
    @Test fun currentWithoutVoltageCannotPromotePower() = runTest {
        val center = center()
        input(center, 500_000, voltage = 0)
        assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_VOLTAGE])
        assertPower(center, CapabilityStatus.UNAVAILABLE)
    }
    @Test fun missingCurrentRevokesCurrentAndPower() = runTest {
        val center = center()
        input(center, 500_000)
        input(center, Int.MIN_VALUE)
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
        assertPower(center, CapabilityStatus.UNAVAILABLE)
    }
    @Test fun missingVoltageAndTemperatureRevokeCapabilities() = runTest {
        val center = center()
        input(center, 500_000)
        input(center, 500_000, voltage = 0, temperature = 0)
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_VOLTAGE])
        assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_TEMPERATURE])
        assertPower(center, CapabilityStatus.UNAVAILABLE)
    }
    @Test fun freshInputsRecoverCapabilities() = runTest {
        val center = center()
        input(center, Int.MIN_VALUE, voltage = 0, temperature = 0)
        input(center, -500_000)
        assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
        assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_VOLTAGE])
        assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[CapabilityType.BATTERY_TEMPERATURE])
        assertPower(center, CapabilityStatus.AVAILABLE)
    }
}
