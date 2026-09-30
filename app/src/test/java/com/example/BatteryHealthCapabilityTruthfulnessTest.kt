package com.example

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
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
class BatteryHealthCapabilityTruthfulnessTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun broadcastHealth(health: Int) {
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_HEALTH, health)
        )
    }

    @Test fun knownBatteryHealthIsAvailable() {
        broadcastHealth(BatteryManager.BATTERY_HEALTH_GOOD)
        val result = CentralCapabilityRegistry(context).detectAllCapabilities()
        assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_HEALTH_STATUS])
    }

    @Test fun unknownBatteryHealthIsUnavailable() {
        broadcastHealth(BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val result = CentralCapabilityRegistry(context).detectAllCapabilities()
        assertEquals(CapabilityStatus.UNAVAILABLE, result[CapabilityType.BATTERY_HEALTH_STATUS])
    }

    @Test fun anyRecognizedHealthValueCountsAsObservable() {
        for (health in listOf(
            BatteryManager.BATTERY_HEALTH_OVERHEAT,
            BatteryManager.BATTERY_HEALTH_DEAD,
            BatteryManager.BATTERY_HEALTH_COLD
        )) {
            broadcastHealth(health)
            val result = CentralCapabilityRegistry(context).detectAllCapabilities()
            assertEquals(CapabilityStatus.AVAILABLE, result[CapabilityType.BATTERY_HEALTH_STATUS])
        }
    }

    @Test fun laterUnknownHealthRevokesAvailability() {
        broadcastHealth(BatteryManager.BATTERY_HEALTH_GOOD)
        val registry = CentralCapabilityRegistry(context)
        assertEquals(
            CapabilityStatus.AVAILABLE,
            registry.detectAllCapabilities()[CapabilityType.BATTERY_HEALTH_STATUS]
        )
        broadcastHealth(BatteryManager.BATTERY_HEALTH_UNKNOWN)
        assertEquals(
            CapabilityStatus.UNAVAILABLE,
            registry.detectAllCapabilities()[CapabilityType.BATTERY_HEALTH_STATUS]
        )
    }
}
