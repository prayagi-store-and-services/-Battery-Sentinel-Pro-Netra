package com.example.service

import android.os.BatteryManager
import java.util.Locale
import kotlin.math.abs

/** Raw values from one ACTION_BATTERY_CHANGED sticky intent plus BATTERY_PROPERTY_CURRENT_NOW. Missing values use null. */
data class BatterySnapshot(
    val status: Int,
    val plugged: Int,
    val level: Int?,
    val scale: Int?,
    val voltageMv: Int?,
    val temperatureTenthsC: Int?,
    val rawCurrentMicroAmps: Int?
)

enum class PowerMode { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }

/** Everything the card shows. Every text is either a real formatted value or "Unavailable"/"Calculating...". */
data class LivePowerUi(
    val mode: PowerMode,
    val voltage: String,
    val current: String,
    val power: String,
    val temperature: String,
    val percentage: String,
    val sessionDuration: String,
    val estimateLabel: String?,
    val estimate: String?
)

/**
 * Pure logic for the Live Power card. The clock is passed in (use SystemClock.elapsedRealtime()),
 * so the session timer is monotonic and unit tests do not need Android.
 * Percentages come only from level/scale. Nothing is estimated from voltage.
 */
class LivePowerTelemetry {
    private data class Point(val atMs: Long, val percent: Double)

    private var sessionMode: PowerMode? = null
    private var sessionStartMs = 0L
    private val window = ArrayDeque<Point>()

    fun reset() { sessionMode = null; window.clear() }

    fun update(s: BatterySnapshot, nowMs: Long): LivePowerUi {
        val mode = classify(s)
        val percent = percentOf(s)

        // The session restarts whenever the mode changes. After a process restart the timer
        // starts from the first observation (it cannot be reconstructed reliably), and says so in the UI.
        val timed = mode == PowerMode.CHARGING || mode == PowerMode.DISCHARGING
        if (!timed) {
            sessionMode = null
            window.clear()
        } else if (mode != sessionMode) {
            sessionMode = mode
            sessionStartMs = nowMs
            window.clear()
        }
        val duration = if (timed) formatDuration((nowMs - sessionStartMs).coerceAtLeast(0L) / 1000L) else UNAVAILABLE

        if (timed && percent != null) {
            val last = window.lastOrNull()
            val inconsistent = last != null && (nowMs <= last.atMs ||
                (mode == PowerMode.CHARGING && percent < last.percent) ||
                (mode == PowerMode.DISCHARGING && percent > last.percent))
            if (inconsistent) window.clear()
            window.addLast(Point(nowMs, percent))
            while (window.isNotEmpty() && nowMs - window.first().atMs > WINDOW_MS) window.removeFirst()
        }

        val currentMa = currentMa(s)
        val showCurrent = currentMa != null && (mode == PowerMode.CHARGING || mode == PowerMode.DISCHARGING)
        val voltage = s.voltageMv?.takeIf { it in 2000..15000 }
        val power = if (voltage != null && showCurrent) voltage.toDouble() * currentMa!! / 1_000_000.0 else null
        val tempC = s.temperatureTenthsC?.takeIf { it in -400..1000 }?.let { it / 10.0 }

        val (label, estimate) = when (mode) {
            PowerMode.CHARGING -> "Estimated Time To Full" to estimateText(percent, true, s.status)
            PowerMode.DISCHARGING -> "Estimated Time Until Empty" to estimateText(percent, false, s.status)
            PowerMode.FULL -> "Estimated Time To Full" to (if (percent != null && percent >= 100.0) "00:00:00" else UNAVAILABLE)
            else -> null to null
        }
        return LivePowerUi(
            mode = mode,
            voltage = voltage?.let { String.format(Locale.US, "%.2f mV", it.toDouble()) } ?: UNAVAILABLE,
            current = if (showCurrent) String.format(Locale.US, "%.2f mA", abs(currentMa!!)) else UNAVAILABLE,
            power = power?.let { String.format(Locale.US, "%.2f W", abs(it)) } ?: UNAVAILABLE,
            temperature = tempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: UNAVAILABLE,
            percentage = percent?.let { String.format(Locale.US, "%.2f%%", it) } ?: UNAVAILABLE,
            sessionDuration = duration,
            estimateLabel = label,
            estimate = estimate
        )
    }

    private fun estimateText(percent: Double?, charging: Boolean, status: Int): String {
        if (percent == null) return UNAVAILABLE
        if (charging && status == BatteryManager.BATTERY_STATUS_FULL) return "00:00:00"
        val remaining = if (charging) 100.0 - percent else percent
        if (remaining <= 0.0) return UNAVAILABLE
        val first = window.firstOrNull() ?: return CALCULATING
        val last = window.last()
        val dtSec = (last.atMs - first.atMs) / 1000.0
        if (dtSec < MIN_SPAN_SEC) return CALCULATING
        val moved = abs(last.percent - first.percent)
        // No change yet in the window: keep calculating for a while, then admit we cannot tell.
        if (moved <= 0.0) return if (dtSec < NO_PROGRESS_SEC) CALCULATING else UNAVAILABLE
        val seconds = remaining / (moved / dtSec)
        if (!seconds.isFinite() || seconds <= 0.0 || seconds > MAX_ETA_SEC) return UNAVAILABLE
        return formatDuration(seconds.toLong())
    }

    companion object {
        const val UNAVAILABLE = "Unavailable"
        const val CALCULATING = "Calculating..."
        private const val WINDOW_MS = 10 * 60_000L
        private const val MIN_SPAN_SEC = 60.0
        private const val NO_PROGRESS_SEC = 300.0
        private const val MAX_ETA_SEC = 48 * 3600.0

        /** Full and Not charging are their own modes and are never reported as charging or discharging. */
        fun classify(s: BatterySnapshot): PowerMode = when {
            s.status == BatteryManager.BATTERY_STATUS_FULL -> PowerMode.FULL
            s.status == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> PowerMode.NOT_CHARGING
            s.status == BatteryManager.BATTERY_STATUS_CHARGING && s.plugged > 0 -> PowerMode.CHARGING
            s.status == BatteryManager.BATTERY_STATUS_DISCHARGING || (s.plugged == 0 && s.status != BatteryManager.BATTERY_STATUS_UNKNOWN) -> PowerMode.DISCHARGING
            else -> PowerMode.UNKNOWN
        }

        fun percentOf(s: BatterySnapshot): Double? {
            val l = s.level ?: return null
            val sc = s.scale ?: return null
            if (l < 0 || sc <= 0 || l > sc) return null
            return l * 100.0 / sc
        }

        /** BATTERY_PROPERTY_CURRENT_NOW is microamps by spec. The sign is not assumed: callers show the magnitude. 0 is treated as no reading. */
        fun currentMa(s: BatterySnapshot): Double? {
            val raw = s.rawCurrentMicroAmps ?: return null
            if (raw == Int.MIN_VALUE || raw == Int.MAX_VALUE || raw == 0) return null
            val ma = ChargingSpeedEngine().normalizeToMilliAmps(raw, true).toDouble()
            return if (abs(ma) <= 30_000.0) ma else null
        }

        fun formatDuration(totalSeconds: Long): String {
            val t = totalSeconds.coerceAtLeast(0L)
            return String.format(Locale.US, "%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
        }
    }
}
