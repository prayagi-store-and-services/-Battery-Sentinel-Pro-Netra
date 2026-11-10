package com.example.service

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryTrackerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearTrackerPrefs() {
        context.getSharedPreferences("battery_tracker", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun parseBatteryData_chargingAc_calculatesCorrectPercentageAndTemperature() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 75)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 315) // 31.5°C
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 4200)
        }

        val data = BatteryTracker.parseBatteryData(intent, null)

        assertEquals(75, data.percentage)
        assertEquals(31.5f, data.temperatureCelsius, 0.001f)
        assertTrue(data.isCharging)
        assertEquals("Charging", data.status)
        assertEquals("AC Charger", data.pluggedSource)
        assertEquals(4200, data.voltageMv)
    }

    @Test
    fun parseBatteryData_discharging_calculatesCorrectValues() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 42)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 264) // 26.4°C
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 3850)
        }

        val data = BatteryTracker.parseBatteryData(intent, null)

        assertEquals(42, data.percentage)
        assertEquals(26.4f, data.temperatureCelsius, 0.001f)
        assertFalse(data.isCharging)
        assertEquals("Discharging", data.status)
        assertEquals("Unplugged", data.pluggedSource)
        assertEquals(3850, data.voltageMv)
    }

    @Test
    fun parseBatteryData_fullBattery_reportsFullStatus() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 100)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 280) // 28.0°C
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }

        val data = BatteryTracker.parseBatteryData(intent, null)

        assertEquals(100, data.percentage)
        assertEquals(28.0f, data.temperatureCelsius, 0.001f)
        assertEquals("Full", data.status)
        assertEquals("USB Port", data.pluggedSource)
    }

    @Test
    fun parseBatteryData_nonStandardScale_scalesCorrectly() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 500)
            putExtra(BatteryManager.EXTRA_SCALE, 1000)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        }

        val data = BatteryTracker.parseBatteryData(intent, null)

        assertEquals(50, data.percentage)
        assertEquals(25.0f, data.temperatureCelsius, 0.001f)
        assertEquals("Not Charging", data.status)
    }

    @Test
    fun buildTrackingNotification_containsPercentageTemperatureAndStatus() {
        val state = BatteryTrackState(
            percentage = 88,
            temperatureCelsius = 29.5f,
            isCharging = true,
            status = "Charging",
            pluggedSource = "AC Charger",
            isTrackingActive = true
        )

        val notification = BatteryTracker.buildTrackingNotification(context, state)

        assertNotNull(notification)
        val shadowNotification = shadowOf(notification)
        val contentTitle = shadowNotification.contentTitle.toString()
        val contentText = shadowNotification.contentText.toString()

        // Unified status notification (v2.1.25): the title is the app name and the single
        // status line carries percentage, temperature and the charging state.
        assertEquals(com.example.Brand.name, contentTitle)
        assertTrue("Line should contain percentage", contentText.contains("88%"))
        assertTrue("Line should contain temperature", contentText.contains("29.5°C"))
        assertTrue("Line should state the charging estimate honestly", contentText.contains("learning your phone's pattern"))
        assertTrue("Notification should be ongoing", (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test
    fun toggle_defaultsToEnabledAndPersistsAcrossReads() {
        assertTrue("Tracker defaults to enabled", BatteryTracker.isEnabled(context))

        BatteryTracker.setEnabled(context, false)
        assertFalse(BatteryTracker.isEnabled(context))
        assertFalse("Toggle persists in prefs", context.getSharedPreferences("battery_tracker", Context.MODE_PRIVATE).getBoolean("enabled", true))
        assertFalse("Published state reflects the toggle", BatteryTracker.batteryState.value.isTrackingActive)

        BatteryTracker.setEnabled(context, true)
        assertTrue(BatteryTracker.isEnabled(context))
        assertTrue(BatteryTracker.batteryState.value.isTrackingActive)
    }

    @Test
    fun onTelemetry_updatesStateOnlyWhenEnabled() {
        BatteryTracker.setEnabled(context, true)
        BatteryTracker.onTelemetry(context, telemetry(level = 67))
        assertEquals(67, BatteryTracker.batteryState.value.percentage)
        assertTrue(BatteryTracker.batteryState.value.isTrackingActive)

        BatteryTracker.setEnabled(context, false)
        BatteryTracker.onTelemetry(context, telemetry(level = 10))
        assertEquals("Disabled tracker does not republish telemetry", 67, BatteryTracker.batteryState.value.percentage)
        assertFalse(BatteryTracker.batteryState.value.isTrackingActive)

        BatteryTracker.setEnabled(context, true)
    }

    private fun telemetry(level: Int) = com.example.model.BatteryTelemetry(
        level = level,
        isCharging = true,
        pluggedType = "AC",
        temperature = 30.0f,
        voltageMv = 4000,
        currentMa = 500
    )
}
