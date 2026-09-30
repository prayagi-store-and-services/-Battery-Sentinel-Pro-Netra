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
class ZeroCurrentCapabilityTruthfulnessTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun setCurrent(current: Int) {
        shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, current)
    }
    @Test fun observedZeroCurrentIsAvailableWithoutFallback() {
        setCurrent(Int.MIN_VALUE)
        val result = CentralCapabilityRegistry(context).detectAllCapabilities(currentMicroAmps = 0, voltageRaw = 4000)
        assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_CURRENT])
        assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_POWER_CALCULATION])
    }
    @Test fun probeZeroIsValidWhenDirectObservationMissing() {
        setCurrent(0)
        val result = CentralCapabilityRegistry(context).detectAllCapabilities(voltageRaw = 4000)
        assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_CURRENT])
    }
    @Test fun absentObservationDefaultsToMissingAndChecksProbe() {
        setCurrent(Int.MIN_VALUE)
        val result = CentralCapabilityRegistry(context).detectAllCapabilities(voltageRaw = 4000)
        assertEquals(CapabilityStatus.UNAVAILABLE, result[CapabilityType.BATTERY_CURRENT])
        assertEquals(CapabilityStatus.UNAVAILABLE, result[CapabilityType.BATTERY_POWER_CALCULATION])
    }
    @Test fun negativeDischargeCurrentRemainsValid() {
        setCurrent(Int.MIN_VALUE)
        val result = CentralCapabilityRegistry(context).detectAllCapabilities(currentMicroAmps = -500_000, voltageRaw = 4000)
        assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_CURRENT])
    }
}
