package com.example.service

import android.os.BatteryManager
import kotlin.math.abs
import kotlin.math.ceil

/** Measured level trend in one uninterrupted session. Never derives runtime from one current reading. */
internal class SessionEtaEstimator {
    data class Estimate(val chargingMinutes: Int? = null, val dischargingMinutes: Int? = null, val allowHistory: Boolean = false)
    private data class Sample(val level: Int, val at: Long)
    private data class Session(val charging: Boolean, val plugged: Int, val target: Int)
    private var session: Session? = null
    private val samples = ArrayDeque<Sample>()
    private var lastSeenAt: Long? = null
    private var finishAt: Long? = null

    fun reset() { session = null; samples.clear(); lastSeenAt = null; finishAt = null }

    fun observe(level: Int?, status: Int, plugged: Int, now: Long, target: Int = 100): Estimate {
        val incoming = when {
            status == BatteryManager.BATTERY_STATUS_CHARGING && plugged > 0 -> Session(true, plugged, target.coerceIn(1, 100))
            status == BatteryManager.BATTERY_STATUS_DISCHARGING && plugged == 0 -> Session(false, plugged, 0)
            else -> null
        }
        if (incoming == null || level == null || level !in 0..100) { reset(); return Estimate() }
        val previous = samples.lastOrNull()
        val seen = lastSeenAt
        val invalidTiming = seen != null && (now < seen || now - seen > MAX_GAP_MS)
        val reversed = previous != null && if (incoming.charging) level < previous.level else level > previous.level
        if (incoming != session || invalidTiming || reversed) reset()
        session = incoming
        lastSeenAt = now
        val changed = samples.lastOrNull()?.level != level
        // Repeated polling must NOT evict the level baseline or restart its elapsed interval.
        // Store level transitions, not the last N one-second polls.
        if (changed) {
            samples.addLast(Sample(level, now))
            while (samples.size > 2 && now - samples.first().at > WINDOW_MS) samples.removeFirst()
            while (samples.size > 101) samples.removeFirst()
            val first = samples.first()
            val elapsed = (now - first.at) / 60_000.0
            val change = abs(level - first.level)
            val remaining = if (incoming.charging) incoming.target - level else level
            if (elapsed >= 2.0 && change >= 2 && remaining > 0) {
                val minutes = remaining * elapsed / change
                val cap = if (incoming.charging) 720 else 1440
                finishAt = if (minutes.isFinite() && minutes > 0 && minutes <= cap) now + (minutes * 60_000).toLong() else null
            } else finishAt = null
        }
        val last = samples.lastOrNull() ?: return Estimate()
        // A stopped/tapered session must not count down to a false zero forever.
        val baseline = samples.first()
        val stepMs = if (last.level != baseline.level) (last.at - baseline.at) / abs(last.level - baseline.level) else 0L
        val stallLimit = maxOf(5 * 60_000L, stepMs * 3)
        if (now - last.at > stallLimit) { finishAt = null; return Estimate() }
        val left = finishAt?.minus(now)?.takeIf { it > 0 } ?: return Estimate(allowHistory = now - samples.first().at < 120_000L)
        val minutes = ceil(left / 60_000.0).toInt()
        return if (incoming.charging) Estimate(chargingMinutes = minutes) else Estimate(dischargingMinutes = minutes)
    }

    companion object {
        private const val MAX_GAP_MS = 30 * 60_000L
        private const val WINDOW_MS = 15 * 60_000L
    }
}
