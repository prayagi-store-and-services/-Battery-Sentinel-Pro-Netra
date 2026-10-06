package com.example

import android.os.BatteryManager
import com.example.service.BatterySnapshot
import com.example.service.LivePowerTelemetry
import com.example.service.PowerMode
import org.junit.Assert.*
import org.junit.Test

class LivePowerTelemetryTest {
    private fun snap(
        status: Int = BatteryManager.BATTERY_STATUS_CHARGING, plugged: Int = 1, level: Int? = 50, scale: Int? = 100,
        mv: Int? = 4000, temp: Int? = 305, raw: Int? = 2_000_000
    ) = BatterySnapshot(status, plugged, level, scale, mv, temp, raw)

    @Test fun formatsChargingFields() {
        val u = LivePowerTelemetry().update(snap(), 0L)
        assertEquals(PowerMode.CHARGING, u.mode)
        assertEquals("4000.00 mV", u.voltage)
        assertEquals("2000.00 mA", u.current)
        assertEquals("8.00 W", u.power)
        assertEquals("30.5 °C", u.temperature)
        assertEquals("50.00%", u.percentage)
        assertEquals("00:00:00", u.sessionDuration)
    }
    @Test fun negativeCurrentShowsMagnitude() {
        val u = LivePowerTelemetry().update(snap(BatteryManager.BATTERY_STATUS_DISCHARGING, 0, raw = -500_000, mv = 3900), 0L)
        assertEquals(PowerMode.DISCHARGING, u.mode)
        assertEquals("500.00 mA", u.current)
        assertEquals("1.95 W", u.power)
    }
    @Test fun fullAndNotChargingAreNotMisclassified() {
        assertEquals(PowerMode.FULL, LivePowerTelemetry.classify(snap(BatteryManager.BATTERY_STATUS_FULL, 1, 100)))
        assertEquals(PowerMode.NOT_CHARGING, LivePowerTelemetry.classify(snap(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 1)))
        val u = LivePowerTelemetry().update(snap(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 1), 0L)
        assertEquals("Unavailable", u.current)
        assertEquals("Unavailable", u.sessionDuration)
    }
    @Test fun missingValuesAreUnavailableNotZero() {
        val u = LivePowerTelemetry().update(snap(mv = null, temp = null, raw = null, level = null), 0L)
        assertEquals("Unavailable", u.voltage); assertEquals("Unavailable", u.current)
        assertEquals("Unavailable", u.power); assertEquals("Unavailable", u.temperature)
        assertEquals("Unavailable", u.percentage)
        assertEquals("Unavailable", LivePowerTelemetry().update(snap(raw = 0), 0L).current)
        assertEquals("Unavailable", LivePowerTelemetry().update(snap(mv = 100), 0L).voltage)
    }
    @Test fun percentNeedsValidScale() {
        assertNull(LivePowerTelemetry.percentOf(snap(level = 50, scale = 0)))
        assertNull(LivePowerTelemetry.percentOf(snap(level = 101, scale = 100)))
        assertEquals(33.33, LivePowerTelemetry.percentOf(snap(level = 1, scale = 3))!!, 0.01)
    }
    @Test fun durationUsesInjectedClockAndResetsOnModeChange() {
        val t = LivePowerTelemetry()
        t.update(snap(), 1_000L)
        assertEquals("01:01:05", t.update(snap(), 1_000L + 3_665_000L).sessionDuration)
        assertEquals("00:00:00", t.update(snap(BatteryManager.BATTERY_STATUS_DISCHARGING, 0), 9_999_999L).sessionDuration)
    }
    @Test fun etaCalculatingThenValue() {
        val t = LivePowerTelemetry()
        assertEquals("Calculating...", t.update(snap(level = 50), 0L).estimate)
        t.update(snap(level = 50), 60_000L)
        // 10 points in 10 minutes => 1 point/min, 40 points left => 40 minutes
        t.update(snap(level = 55), 300_000L)
        assertEquals("00:40:00", t.update(snap(level = 60), 600_000L).estimate)
    }
    @Test fun etaCountsDownToTargetAndNeverClimbs() {
        val t = LivePowerTelemetry()
        t.targetPercent = 80
        t.update(snap(level = 50), 0L)
        t.update(snap(level = 50), 60_000L)
        t.update(snap(level = 55), 300_000L)
        val first = t.update(snap(level = 60), 600_000L)
        assertEquals("Estimated Time To 80%", first.estimateLabel)
        assertEquals("00:20:00", first.estimate)
        assertEquals("00:19:00", t.update(snap(level = 60), 660_000L).estimate)
        assertEquals("00:18:00", t.update(snap(level = 60), 720_000L).estimate)
    }
    @Test fun etaIsZeroOnceTargetReached() {
        val t = LivePowerTelemetry()
        t.targetPercent = 80
        t.update(snap(level = 79), 0L)
        t.update(snap(level = 79), 60_000L)
        assertEquals("00:00:00", t.update(snap(level = 80), 120_000L).estimate)
    }
    @Test fun etaUnavailableWhenNoProgress() {
        val t = LivePowerTelemetry()
        t.update(snap(level = 50), 0L)
        assertEquals("Calculating...", t.update(snap(level = 50), 120_000L).estimate)
        assertEquals("Unavailable", t.update(snap(level = 50), 360_000L).estimate)
    }
    @Test fun etaResetsWhenChargingPercentFalls() {
        val t = LivePowerTelemetry()
        t.update(snap(level = 60), 0L)
        assertEquals("Calculating...", t.update(snap(level = 58), 90_000L).estimate)
    }
    @Test fun dischargeEtaNeverNegativeOrDivideByZero() {
        val t = LivePowerTelemetry()
        val d = BatteryManager.BATTERY_STATUS_DISCHARGING
        t.update(snap(d, 0, 50), 0L)
        assertEquals("Unavailable", t.update(snap(d, 0, 0), 600_000L).estimate)
        val t2 = LivePowerTelemetry()
        t2.update(snap(d, 0, 60), 0L)
        assertEquals("00:50:00", t2.update(snap(d, 0, 50), 600_000L).estimate)
    }
    @Test fun fullShowsZeroOnlyWhenDeviceConfirms() {
        assertEquals("00:00:00", LivePowerTelemetry().update(snap(BatteryManager.BATTERY_STATUS_FULL, 1, 100), 0L).estimate)
        assertEquals("Unavailable", LivePowerTelemetry().update(snap(BatteryManager.BATTERY_STATUS_FULL, 1, 90), 0L).estimate)
    }
    @Test fun formatDurationClampsNegative() {
        assertEquals("00:00:00", LivePowerTelemetry.formatDuration(-5))
    }
}
