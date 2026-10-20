package com.example

import com.example.service.SentinelStatusNotification
import com.example.service.SentinelStatusNotification.StatusLineMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelNotificationTextTest {

    @Test
    fun chargingConfidentShowsLearnedEta() {
        assertEquals(
            "62% - 34.2°C - ~48 min to full",
            SentinelStatusNotification.buildLine(62, 34.2f, StatusLineMode.CHARGING, 48, true)
        )
    }

    @Test
    fun chargingLongEtaUsesHoursAndMinutes() {
        assertEquals(
            "20% - 31.0°C - ~3h 20m to full",
            SentinelStatusNotification.buildLine(20, 31.0f, StatusLineMode.CHARGING, 200, true)
        )
    }

    @Test
    fun chargingWithoutConfidenceSaysLearning() {
        assertEquals(
            "62% - 34.2°C - learning your phone's pattern",
            SentinelStatusNotification.buildLine(62, 34.2f, StatusLineMode.CHARGING, 48, false)
        )
    }

    @Test
    fun onBatteryConfidentShowsRemaining() {
        assertEquals(
            "62% - 34.2°C - ~3h 20m remaining",
            SentinelStatusNotification.buildLine(62, 34.2f, StatusLineMode.ON_BATTERY, 200, true)
        )
    }

    @Test
    fun onBatteryWithoutConfidenceSaysLearning() {
        assertEquals(
            "62% - 34.2°C - learning your phone's pattern",
            SentinelStatusNotification.buildLine(62, 34.2f, StatusLineMode.ON_BATTERY, null, false)
        )
    }

    @Test
    fun connectedNotChargingSaysSoWithoutInventingAnEta() {
        assertEquals(
            "62% - 34.2°C - connected, not charging",
            SentinelStatusNotification.buildLine(62, 34.2f, StatusLineMode.CONNECTED_NOT_CHARGING, null, false)
        )
    }

    @Test
    fun staleDataIsLabelled() {
        assertEquals(
            "Last-known battery data - live update unavailable",
            SentinelStatusNotification.buildLine(62, 34.2f, StatusLineMode.CHARGING, 48, true, dataFresh = false)
        )
    }

    @Test
    fun missingTemperatureIsLeftOut() {
        assertEquals(
            "62% - ~48 min to full",
            SentinelStatusNotification.buildLine(62, null, StatusLineMode.CHARGING, 48, true)
        )
    }

    @Test
    fun missingLevelIsHonest() {
        assertEquals(
            "Battery - 34.2°C - ~48 min to full",
            SentinelStatusNotification.buildLine(null, 34.2f, StatusLineMode.CHARGING, 48, true)
        )
    }
}
