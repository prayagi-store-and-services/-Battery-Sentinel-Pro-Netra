package com.example

import android.os.BatteryManager
import com.example.service.MeasuredEtaPolicy
import com.example.service.SessionEtaEstimator
import org.junit.Assert.*
import org.junit.Test

class MeasuredEtaPolicyTest {
    private val charging = BatteryManager.BATTERY_STATUS_CHARGING
    private val discharge = BatteryManager.BATTERY_STATUS_DISCHARGING
    // A SYNTHETIC learned phone: fast up to80,3min/%above80. Not a universal phone rule.
    private fun curve(from: Int, to: Int, charging: Boolean): MeasuredEtaPolicy.Curve? {
        val levels = if (charging) (from + 1)..to else (to + 1)..from
        return MeasuredEtaPolicy.Curve(levels.sumOf { if (it > 80) 3.0 else 1.0 }, true)
    }
    private fun select(s: SessionEtaEstimator.Estimate, level: Int, target: Int = 100, c: Boolean = true) =
        MeasuredEtaPolicy.select(s, level, target, c, ::curve)

    @Test fun learnedTailIsNotReplacedByFastEarlierSessionAverage() {
        val e = SessionEtaEstimator()
        for (level in 70..80 step 2) e.observe(level, charging, 1, (level - 70) * 60_000L)
        e.observe(81, charging, 1, 13 * 60_000L)
        val session = e.observe(82, charging, 1, 16 * 60_000L)
        assertEquals(26, session.chargingMinutes) // old linear result
        assertEquals(54, select(session, 82)) // known measured tail shape
    }
    @Test fun currentPaceScalesHistoryFasterAndSlower() {
        for (duration in listOf(2, 4, 8)) {
            val e = SessionEtaEstimator(); e.observe(70, charging, 1, 0)
            val s = e.observe(74, charging, 1, duration * 60_000L)
            assertEquals(kotlin.math.ceil(66.0 * duration / 4).toInt(), select(s, 74))
        }
    }
    @Test fun incompleteOrInvalidHistoryKeepsMeasuredFallback() {
        val e = SessionEtaEstimator(); e.observe(50, charging, 1, 0)
        val s = e.observe(52, charging, 1, 120_000)
        for (value in listOf<MeasuredEtaPolicy.Curve?>(null, MeasuredEtaPolicy.Curve(200.0, false), MeasuredEtaPolicy.Curve(Double.NaN, true), MeasuredEtaPolicy.Curve(0.0, true))) {
            assertEquals(48, MeasuredEtaPolicy.select(s, 52, 100, true) { _, _, _ -> value })
        }
        assertEquals(48, MeasuredEtaPolicy.select(s, 52, 100, true) { from, _, _ -> if (from == 52) MeasuredEtaPolicy.Curve(60.0, true) else null })
    }
    @Test fun oppositeDirectionPauseAndStallCannotReviveHistory() {
        val e = SessionEtaEstimator(); e.observe(70, charging, 1, 0)
        val s = e.observe(72, charging, 1, 120_000)
        assertNull(select(s, 72, 0, false))
        assertNull(select(e.observe(72, charging, 1, 600_001), 72))
        assertNull(select(e.observe(72, BatteryManager.BATTERY_STATUS_NOT_CHARGING, 1, 601_000), 72))
    }
    @Test fun targetOrChargerChangeClearsOldMeasuredPace() {
        val e = SessionEtaEstimator(); e.observe(70, charging, 1, 0, 90)
        assertNotNull(select(e.observe(72, charging, 1, 120_000, 90), 72, 90))
        // Warm-up may use history, but not the previous target's measured pace.
        val changed = e.observe(72, charging, 1, 121_000, 95)
        assertNull(changed.progress); assertEquals(53, select(changed, 72, 95))
        val changedPlug = e.observe(72, charging, 2, 122_000, 95)
        assertNull(changedPlug.progress)
    }
    @Test fun countdownAndDischargeUseSamePolicy() {
        val e = SessionEtaEstimator(); e.observe(82, discharge, 0, 0)
        e.observe(80, discharge, 0, 360_000)
        val s = e.observe(78, discharge, 0, 480_000)
        assertEquals(78, select(s, 78, 0, false))
        assertEquals(77, select(e.observe(78, discharge, 0, 540_000), 78, 0, false))
    }
    @Test fun unknownHistoryWarmupAndReachedTargetStayHonest() {
        val e = SessionEtaEstimator()
        val s = e.observe(80, charging, 1, 0)
        assertEquals(60, select(s, 80))
        assertNull(MeasuredEtaPolicy.select(s, 80, 100, true) { _, _, _ -> null })
        assertNull(select(s, 80, 80))
        assertNull(select(e.observe(null, charging, 1, 1000), 80))
    }

    @Test fun liveTelemetryUsesSameTailAndKeepsApproximateLabel() {
        val t = com.example.service.LivePowerTelemetry().apply { etaCurve = { from, to, mode, _ -> curve(from, to, mode) } }
        fun snap(level: Int, status: Int = charging) = com.example.service.BatterySnapshot(status, 1, level, 100, 4200, 250, 1_000_000)
        for (level in 70..80 step 2) t.update(snap(level), (level - 70) * 60_000L)
        t.update(snap(81), 780_000)
        assertEquals("~00:54:00 (est.)", t.update(snap(82), 960_000).estimate)
        t.targetPercent = 90
        assertEquals("~00:24:00 (est.)", t.update(snap(82), 961_000).estimate)
        assertNull(t.update(snap(82, BatteryManager.BATTERY_STATUS_NOT_CHARGING), 962_000).estimate)
    }
}
