package com.example.service

import kotlin.math.ceil

/** One estimate-selection rule. No new observations, persistence or universal taper threshold. */
internal object MeasuredEtaPolicy {
    data class Curve(val minutes: Double, val confident: Boolean)
    fun select(
        session: SessionEtaEstimator.Estimate,
        level: Int?, target: Int, charging: Boolean,
        curve: (Int, Int, Boolean) -> Curve?
    ): Int? {
        val measured = if (charging) session.chargingMinutes else session.dischargingMinutes
        if (session.modeCharging != charging) return null
        if (level == null || level !in 0..100 || target !in 0..100 ||
            (if (charging) target <= level else target >= level)) return null
        val tail = curve(level, target, charging)?.takeIf { it.confident && it.minutes.isFinite() && it.minutes > 0 }
        val progress = session.progress
        val estimate = if (tail != null && progress != null) {
            val observed = curve(progress.fromLevel, progress.toLevel, charging)
                ?.takeIf { it.confident && it.minutes.isFinite() && it.minutes > 0 }
            if (observed == null) return measured
            // Compare the same level range, not a whole-session average against the slow tail.
            // A faster/slower session scales the learned remaining shape in either direction.
            tail.minutes * (progress.minutes / observed.minutes) - progress.sinceLastMinutes
        } else if (tail != null && session.allowHistory) tail.minutes else return measured
        val cap = if (charging) 720 else 1440
        if (!estimate.isFinite() || estimate <= 0 || estimate > cap) return measured
        return ceil(estimate).toInt()
    }

    fun learned(from: Int, to: Int, charging: Boolean, temperature: Float?): Curve? =
        ChargeCurveLearner.estimateMinutes(from, to, charging, temperature)?.let { Curve(it.exactMinutes, it.confident) }
}
