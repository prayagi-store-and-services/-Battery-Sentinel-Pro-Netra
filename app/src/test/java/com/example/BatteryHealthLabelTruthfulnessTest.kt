package com.example

import android.app.Application
import android.os.BatteryManager
import com.example.service.BatteryMonitorService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BatteryHealthLabelTruthfulnessTest {
    @Test fun everyRecognizedHealthValueHasItsOwnLabel() {
        val labels = mapOf(
            BatteryManager.BATTERY_HEALTH_GOOD to "Good",
            BatteryManager.BATTERY_HEALTH_OVERHEAT to "Overheat",
            BatteryManager.BATTERY_HEALTH_DEAD to "Dead",
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE to "Over Voltage",
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE to "Unspecified Failure",
            BatteryManager.BATTERY_HEALTH_COLD to "Cold"
        )
        labels.forEach { (health, expected) ->
            assertEquals(expected, BatteryMonitorService.reportedHealthLabel(health))
        }
    }

    @Test fun missingUnknownAndInvalidHealthNeverGetAnOldLabel() {
        for (health in listOf(BatteryManager.BATTERY_HEALTH_UNKNOWN, -1, 0, 8, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals("Unavailable", BatteryMonitorService.reportedHealthLabel(health))
        }
    }

    @Test fun laterUnknownOrFailureCannotKeepGoodLabel() {
        assertEquals("Good", BatteryMonitorService.reportedHealthLabel(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals("Unavailable", BatteryMonitorService.reportedHealthLabel(BatteryManager.BATTERY_HEALTH_UNKNOWN))
        assertEquals("Unspecified Failure", BatteryMonitorService.reportedHealthLabel(BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE))
    }
}
