package com.example.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryForegroundServiceTest {

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

        val data = BatteryForegroundService.parseBatteryData(intent, null)

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

        val data = BatteryForegroundService.parseBatteryData(intent, null)

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

        val data = BatteryForegroundService.parseBatteryData(intent, null)

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

        val data = BatteryForegroundService.parseBatteryData(intent, null)

        assertEquals(50, data.percentage)
        assertEquals(25.0f, data.temperatureCelsius, 0.001f)
        assertEquals("Not Charging", data.status)
    }

    @Test
    fun buildTrackingNotification_containsPercentageTemperatureAndStatus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = BatteryTrackState(
            percentage = 88,
            temperatureCelsius = 29.5f,
            isCharging = true,
            status = "Charging",
            pluggedSource = "AC Charger",
            isTrackingActive = true
        )

        val notification = BatteryForegroundService.buildTrackingNotification(context, state)

        assertNotNull(notification)
        val shadowNotification = shadowOf(notification)
        val contentTitle = shadowNotification.contentTitle.toString()
        val contentText = shadowNotification.contentText.toString()

        assertTrue("Title should contain percentage", contentTitle.contains("88%"))
        assertTrue("Title should contain status", contentTitle.contains("Charging"))
        assertTrue("Text should contain temperature", contentText.contains("29.5°C"))
        assertTrue("Text should contain status", contentText.contains("Charging"))
        assertTrue("Notification should be ongoing", (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test
    fun serviceLifecycle_startsAsForegroundServiceAndHandlesIntent() {
        val controller = Robolectric.buildService(BatteryForegroundService::class.java)
        val service = controller.create().get()

        assertNotNull(service)

        // Start command
        controller.startCommand(0, 0)
        assertTrue(BatteryForegroundService.isRunning())

        // Stop command
        val stopIntent = Intent().apply {
            action = BatteryForegroundService.ACTION_STOP_SERVICE
        }
        controller.startCommand(0, 1)

        // Destroy
        controller.destroy()
        assertFalse(BatteryForegroundService.isRunning())
    }
}
