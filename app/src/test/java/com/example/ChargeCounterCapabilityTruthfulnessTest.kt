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
class ChargeCounterCapabilityTruthfulnessTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun setCounter(value: Int) {
        shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .setIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, value)
    }
    @Test fun observedZeroCounterIsAvailable() {
        setCounter(0)
        assertEquals(CapabilityStatus.AVAILABLE,
            CentralCapabilityRegistry(context).detectAllCapabilities()[CapabilityType.BATTERY_CHARGE_COUNTER])
    }
    @Test fun positiveCounterIsAvailable() {
        setCounter(2_000_000)
        assertEquals(CapabilityStatus.AVAILABLE,
            CentralCapabilityRegistry(context).detectAllCapabilities()[CapabilityType.BATTERY_CHARGE_COUNTER])
    }
    @Test fun unsupportedSentinelIsUnavailable() {
        setCounter(Int.MIN_VALUE)
        assertEquals(CapabilityStatus.UNAVAILABLE,
            CentralCapabilityRegistry(context).detectAllCapabilities()[CapabilityType.BATTERY_CHARGE_COUNTER])
    }
    @Test fun invalidNegativeCounterIsUnavailable() {
        setCounter(-1)
        assertEquals(CapabilityStatus.UNAVAILABLE,
            CentralCapabilityRegistry(context).detectAllCapabilities()[CapabilityType.BATTERY_CHARGE_COUNTER])
    }
    @Test fun lostCounterRevokesAvailability() {
        setCounter(2_000_000)
        val registry = CentralCapabilityRegistry(context)
        assertEquals(CapabilityStatus.AVAILABLE,
            registry.detectAllCapabilities()[CapabilityType.BATTERY_CHARGE_COUNTER])
        setCounter(Int.MIN_VALUE)
        assertEquals(CapabilityStatus.UNAVAILABLE,
            registry.detectAllCapabilities()[CapabilityType.BATTERY_CHARGE_COUNTER])
    }
}
