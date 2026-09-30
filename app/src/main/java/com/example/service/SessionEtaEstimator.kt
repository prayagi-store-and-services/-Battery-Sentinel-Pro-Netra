package com.example.service

import android.os.BatteryManager
import kotlin.math.ceil

/** Linear percentage progression from one uninterrupted observed session, not a guarantee. */
internal class SessionEtaEstimator {
    data class Estimate(val chargingMinutes: Int? = null, val dischargingMinutes: Int? = null)
    private data class Sample(val level: Int, val at: Long)
    private data class Session(val charging: Boolean, val plugged: Int)
    private var session: Session? = null
    private val samples = mutableListOf<Sample>()

    fun observe(level: Int?, status: Int, plugged: Int, now: Long): Estimate {
        val incoming = when {
            status == BatteryManager.BATTERY_STATUS_CHARGING && plugged > 0 -> Session(true, plugged)
            status == BatteryManager.BATTERY_STATUS_DISCHARGING && plugged == 0 -> Session(false, plugged)
            else -> null
        }
        if (incoming == null || level == null || level !in 0..100) {
            session = null
            samples.clear()
            return Estimate()
        }
        val previous = samples.lastOrNull()
        val invalidTiming = previous != null && (now <= previous.at || now - previous.at > MAX_GAP_MS)
        val reversed = previous != null && if (incoming.charging) level < previous.level else level > previous.level
        if (incoming != session || invalidTiming || reversed) samples.clear()
        session = incoming
        samples.add(Sample(level, now))
        samples.removeAll { now - it.at > WINDOW_MS }
        while (samples.size > 20) samples.removeAt(0)
        val first = samples.first()
        val elapsed = (now - first.at) / 60_000.0
        val change = level - first.level
        if (elapsed < 2.0 || (incoming.charging && change <= 0) || (!incoming.charging && change >= 0)) return Estimate()
        val remaining = if (incoming.charging) 100 - level else level
        if (remaining == 0) return Estimate()
        val minutes = ceil(remaining * elapsed / kotlin.math.abs(change)).toInt()
        // Outside the supported display range is unavailable, never a fabricated capped value.
        return if (incoming.charging && minutes in 1..720) Estimate(chargingMinutes = minutes)
        else if (!incoming.charging && minutes in 1..1440) Estimate(dischargingMinutes = minutes)
        else Estimate()
    }

    companion object {
        // No progression across long observation gaps; bounded recent sample window.
        private const val MAX_GAP_MS = 30 * 60_000L
        private const val WINDOW_MS = 60 * 60_000L
    }
}
