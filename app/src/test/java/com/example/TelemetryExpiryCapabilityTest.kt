package com.example

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.model.FieldStatus
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
class TelemetryExpiryCapabilityTest {
    private val types = listOf(CapabilityType.BATTERY_TEMPERATURE, CapabilityType.BATTERY_VOLTAGE,
        CapabilityType.BATTERY_CURRENT, CapabilityType.BATTERY_POWER_CALCULATION,
        CapabilityType.CHARGING_SPEED_CALCULATION, CapabilityType.FAST_CHARGING_DETECTION)
    private fun center(clock: () -> Long): NetraCentralDataCenter {
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, Int.MIN_VALUE)
        return NetraCentralDataCenter(clock).also { center ->
            NetraCentralDataCenter::class.java.getDeclaredField("capabilityRegistry").apply {
                isAccessible = true
                set(center, CentralCapabilityRegistry(context))
            }
            center.refreshCapabilities()
        }
    }
    private suspend fun input(center: NetraCentralDataCenter) {
        center.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 1_000_000, null, null)
    }
    @Test fun expiredFieldsRevokeMeasuredCapabilitiesWithoutRefresh() = runTest {
        var now = 1_000L
        val center = center { now }
        input(center)
        types.forEach { assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[it]) }
        now += 120_001L
        center.expireTelemetryFreshness()
        types.forEach { assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[it]) }
        assertEquals(FieldStatus.LAST_VALID, center.centralState.value.fieldStates.currentStatus)
        assertEquals(1000, center.centralState.value.currentMa)
    }
    @Test fun fieldsAtFreshnessBoundaryKeepCapabilities() = runTest {
        var now = 1_000L
        val center = center { now }
        input(center)
        now += 120_000L
        center.expireTelemetryFreshness()
        types.forEach { assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[it]) }
    }
    @Test fun backwardClockRevokesCapabilities() = runTest {
        var now = 1_000L
        val center = center { now }
        input(center)
        now = 999L
        center.expireTelemetryFreshness()
        types.forEach { assertEquals(CapabilityStatus.UNAVAILABLE, center.centralState.value.capabilities[it]) }
    }
    @Test fun newObservationRecoversExpiredCapabilities() = runTest {
        var now = 1_000L
        val center = center { now }
        input(center)
        now += 120_001L
        center.expireTelemetryFreshness()
        input(center)
        types.forEach { assertEquals(CapabilityStatus.AVAILABLE, center.centralState.value.capabilities[it]) }
    }
}
