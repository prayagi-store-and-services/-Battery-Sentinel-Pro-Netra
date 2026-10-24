package com.example.util

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryHardwareFactsTest {
    private fun base() = Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, 50)
        .putExtra(BatteryManager.EXTRA_SCALE, 100)

    @Test fun absentSensorsRemainUnavailableWhileLevelSurvives() {
        val facts = BatteryHardwareFacts.fromIntent(base())!!
        assertEquals(50, facts.levelPercent)
        assertNull(facts.temperatureC)
        assertNull(facts.voltageMv)
    }

    @Test fun reportedSensorsAreDecodedWithoutEstimation() {
        val facts = BatteryHardwareFacts.fromIntent(base()
            .putExtra(BatteryManager.EXTRA_TEMPERATURE, 325)
            .putExtra(BatteryManager.EXTRA_VOLTAGE, 4100))!!
        assertEquals(32.5f, facts.temperatureC!!, 0f)
        assertNotNull(facts.voltageMv)
        assertEquals(4100, facts.voltageMv!!.toInt())
    }

    @Test fun actualFreezingAndSubzeroTemperatureAreNotMissing() {
        for (raw in listOf(0, -1, -50)) {
            val facts = BatteryHardwareFacts.fromIntent(base()
                .putExtra(BatteryManager.EXTRA_TEMPERATURE, raw))!!
            assertEquals(raw / 10f, facts.temperatureC!!, 0f)
        }
    }

    @Test fun nonPositiveVoltageAndTemperatureSentinelAreUnavailable() {
        for (raw in listOf(-1, 0, Int.MIN_VALUE)) {
            assertNull(BatteryHardwareFacts.fromIntent(base()
                .putExtra(BatteryManager.EXTRA_VOLTAGE, raw))!!.voltageMv)
        }
        assertNull(BatteryHardwareFacts.fromIntent(base()
            .putExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE))!!.temperatureC)
    }

    @Test fun invalidRequiredLevelOrScaleStillRejectsFacts() {
        assertNull(BatteryHardwareFacts.fromIntent(Intent()))
        assertNull(BatteryHardwareFacts.fromIntent(base().putExtra(BatteryManager.EXTRA_SCALE, 0)))
        assertNull(BatteryHardwareFacts.fromIntent(base().putExtra(BatteryManager.EXTRA_LEVEL, -1)))
    }
}
