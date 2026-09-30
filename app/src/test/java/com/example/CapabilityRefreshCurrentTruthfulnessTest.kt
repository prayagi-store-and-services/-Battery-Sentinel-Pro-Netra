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
class CapabilityRefreshCurrentTruthfulnessTest {
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
    @Test fun startupWithoutCurrentDoesNotInventZeroObservation() {
        val center = center()
        center.refreshCapabilities()
        assertEquals(CapabilityStatus.UNAVAILABLE,
            center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
    }
    @Test fun voltageWithoutCurrentCannotAdvertisePower() = runTest {
        val center = center()
        center.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING,
            0, 300, 4000, Int.MIN_VALUE, null, null)
        center.refreshCapabilities()
        assertEquals(CapabilityStatus.UNAVAILABLE,
            center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
        assertEquals(CapabilityStatus.UNAVAILABLE,
            center.centralState.value.capabilities[CapabilityType.BATTERY_POWER_CALCULATION])
    }
    @Test fun measuredZeroRemainsAvailableAfterRefresh() = runTest {
        val center = center()
        center.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING,
            0, 300, 4000, 0, null, null)
        center.refreshCapabilities()
        assertEquals(CapabilityStatus.AVAILABLE,
            center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
        assertEquals(CapabilityStatus.AVAILABLE,
            center.centralState.value.capabilities[CapabilityType.BATTERY_POWER_CALCULATION])
    }
    @Test fun measuredDischargeRemainsAvailableAfterRefresh() = runTest {
        val center = center()
        center.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING,
            0, 300, 4000, -500_000, null, null)
        center.refreshCapabilities()
        assertEquals(CapabilityStatus.AVAILABLE,
            center.centralState.value.capabilities[CapabilityType.BATTERY_CURRENT])
    }
}
