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
    val estimate: String?,
    val currentStats: CurrentSampleStats? = null
)

/** Foreground sample statistics only, never adapter capability or a time-weighted average. */
data class CurrentSampleStats(val minimumMa: Double, val maximumMa: Double, val averageMa: Double, val samples: Long)

/**
 * Pure logic for the Live Power card. The clock is passed in (use SystemClock.elapsedRealtime()),
 * so the session timer is monotonic and unit tests do not need Android.
 * Percentages come only from level/scale. Nothing is estimated from voltage.
 */
class LivePowerTelemetry(
    private val sessionStartEpochMs: (PowerMode) -> Long? = { null },
    private val wallClock: () -> Long = { System.currentTimeMillis() }
) {
    private val etaEstimator = SessionEtaEstimator()
    private var sessionMode: PowerMode? = null
    private var sessionStartMs = 0L
    private var canonicalEpochMs: Long? = null
    private var canonicalElapsedMs: Long? = null
    private var currentMode: PowerMode? = null
    private var lastCurrentAt: Long? = null
    private var currentMin = 0.0
    private var currentMax = 0.0
    private var currentMean = 0.0
    private var currentCount = 0L

    private fun clearCurrentSamples() { currentMode = null; lastCurrentAt = null; currentCount = 0L }
    private fun observeCurrent(mode: PowerMode, ma: Double?, nowMs: Long): CurrentSampleStats? {
        if (ma == null || (mode != PowerMode.CHARGING && mode != PowerMode.DISCHARGING)) {
            clearCurrentSamples(); return null
        }
        val previousAt = lastCurrentAt
        if (mode != currentMode || previousAt == null || nowMs <= previousAt || nowMs - previousAt > 5_000L) {
            clearCurrentSamples()
        }
        val magnitude = abs(ma)
        if (currentCount == 0L) { currentMin = magnitude; currentMax = magnitude; currentMean = magnitude }
        else { currentMin = minOf(currentMin, magnitude); currentMax = maxOf(currentMax, magnitude) }
        currentCount++
        currentMean += (magnitude - currentMean) / currentCount
        currentMode = mode; lastCurrentAt = nowMs
        return CurrentSampleStats(currentMin, currentMax, currentMean, currentCount)
    }

    /** Charge level the estimate counts down to (the user's target, 100 when not set). */
    var targetPercent: Int = 100

    fun reset() { clearCurrentSamples(); sessionMode = null; canonicalEpochMs = null; canonicalElapsedMs = null; etaEstimator.reset() }

    fun update(s: BatterySnapshot, nowMs: Long): LivePowerUi {
        val mode = classify(s)
        val percent = percentOf(s)

        // Sampling/ETA evidence always starts with real observations. Only the displayed duration
        // may reuse a validated Central Unit start; no pre-restart samples are invented.
        val timed = mode == PowerMode.CHARGING || mode == PowerMode.DISCHARGING
        if (!timed) {
            sessionMode = null
            canonicalEpochMs = null
            canonicalElapsedMs = null
            etaEstimator.reset()
        } else if (mode != sessionMode) {
            sessionMode = mode
            sessionStartMs = nowMs
            canonicalEpochMs = null
            canonicalElapsedMs = null
            etaEstimator.reset()
        }
        if (timed) {
            val nowEpoch = wallClock()
            val start = CanonicalSessionTime.validStart(sessionStartEpochMs(mode), nowEpoch)
            if (start != null && start != canonicalEpochMs) {
                if (canonicalEpochMs != null) {
                    sessionStartMs = nowMs
                    etaEstimator.reset()
                    clearCurrentSamples()
                }
                canonicalEpochMs = start
                canonicalElapsedMs = CanonicalSessionTime.elapsedStart(start, nowEpoch, nowMs)
            }
        }
        val duration = if (timed) formatDuration((nowMs - (canonicalElapsedMs ?: sessionStartMs)).coerceAtLeast(0L) / 1000L) else UNAVAILABLE

        val currentMa = currentMa(s)
        val showCurrent = currentMa != null && (mode == PowerMode.CHARGING || mode == PowerMode.DISCHARGING)
        val voltage = s.voltageMv?.takeIf { it in 2000..15000 }
        val power = if (voltage != null && showCurrent) voltage.toDouble() * currentMa!! / 1_000_000.0 else null
        val tempC = s.temperatureTenthsC?.takeIf { it in -400..1000 }?.let { it / 10.0 }

        val (label, estimate) = when (mode) {
            PowerMode.CHARGING -> (if (targetPercent in 1..99) "Estimated Time To $targetPercent%" else "Estimated Time To Full") to estimateText(s, percent, true, nowMs)
            PowerMode.DISCHARGING -> "Estimated Time Until Empty" to estimateText(s, percent, false, nowMs)
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
            estimate = estimate,
            currentStats = observeCurrent(mode, currentMa, nowMs)
        )
    }

    private fun estimateText(s: BatterySnapshot, percent: Double?, charging: Boolean, nowMs: Long): String {
        val result = etaEstimator.observe(percent?.toInt(), s.status, s.plugged, nowMs, targetPercent)
        if (percent == null) return UNAVAILABLE
        if (charging && percent >= targetPercent.coerceIn(1, 100)) return "00:00:00"
        val minutes = if (charging) result.chargingMinutes else result.dischargingMinutes
        return minutes?.let { "~" + formatDuration(it * 60L) + " (est.)" }
            ?: if (nowMs - sessionStartMs < 120_000L) CALCULATING else UNAVAILABLE
    }

    companion object {
        const val UNAVAILABLE = "Unavailable"
        const val CALCULATING = "Calculating..."

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
            // Follow the Android API unit, not magnitude guesses (5000 microamps is 5 mA, not 5000 mA).
            val ma = raw.toDouble() / 1000.0
            return if (abs(ma) <= 30_000.0) ma else null
        }

        fun formatDuration(totalSeconds: Long): String {
            val t = totalSeconds.coerceAtLeast(0L)
            return String.format(Locale.US, "%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
        }
    }
}
