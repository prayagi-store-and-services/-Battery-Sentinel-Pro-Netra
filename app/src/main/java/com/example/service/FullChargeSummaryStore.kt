package com.example.service

import android.content.Context

/** Completed observations only. No in-progress state is restored across process gaps. */
class FullChargeSummaryStore(context: Context) {
    data class Completed(val disconnectedAt: Long, val observation: FullChargeObservation.Summary)
    private val p = context.applicationContext.getSharedPreferences("netra_full_observation_v1", Context.MODE_PRIVATE)

    @Synchronized fun load(): Completed? = try {
        val end = p.getLong("end", 0)
        val full = p.getLong("full", 0)
        val start = p.getInt("start", -1)
        val before = p.getLong("before", -1)
        val after = p.getLong("after", -1)
        if (end <= 0 || full <= 0 || full > end || start !in 0..99 || before < 0 || after < 0 ||
            before > MAX_INTERVAL || after > MAX_INTERVAL ||
            kotlin.math.abs((end - full) - after) > FullChargeObservation.CLOCK_TOLERANCE_MS) null
        else Completed(end, FullChargeObservation.Summary(full, start, before, after))
    } catch (_: RuntimeException) { null }

    @Synchronized fun save(value: Completed): Boolean = try {
        val s = value.observation
        if (value.disconnectedAt <= 0 || s.firstFullWallMs <= 0 || s.firstFullWallMs > value.disconnectedAt ||
            s.startPercent !in 0..99 || s.observedToFullMs !in 0..MAX_INTERVAL ||
            s.connectedAfterFullMs !in 0..MAX_INTERVAL ||
            kotlin.math.abs((value.disconnectedAt - s.firstFullWallMs) - s.connectedAfterFullMs) > FullChargeObservation.CLOCK_TOLERANCE_MS) return false
        // Reserve the announcement as not-yet-consumed in the same atomic preference edit.
        p.edit().putLong("end", value.disconnectedAt).putLong("full", s.firstFullWallMs)
            .putInt("start", s.startPercent).putLong("before", s.observedToFullMs)
            .putLong("after", s.connectedAfterFullMs).putBoolean("consumed", false).commit()
    } catch (_: RuntimeException) { false }

    /** Consume only the event that produced this summary. Never replay on app restart. */
    @Synchronized fun consume(disconnectedAt: Long): Completed? {
        val value = load()?.takeIf { it.disconnectedAt == disconnectedAt } ?: return null
        return try {
            if (p.getBoolean("consumed", true) || !p.edit().putBoolean("consumed", true).commit()) null else value
        } catch (_: RuntimeException) { null }
    }
    companion object { const val MAX_INTERVAL = 7L * 24 * 60 * 60_000 }
}

internal fun observedIntervalSpeech(ms: Long): String = when {
    ms < 60_000 -> "less than one minute"
    ms < 120_000 -> "about one minute"
    else -> "about ${ms / 60_000} minutes"
}

fun fullChargeSummarySpeech(s: FullChargeObservation.Summary): String =
    "Battery was first observed at one hundred percent after ${observedIntervalSpeech(s.observedToFullMs)} of connected observation from ${s.startPercent} percent. " +
        "It stayed connected for ${observedIntervalSpeech(s.connectedAfterFullMs)} before this disconnect. These are observed intervals, not exact charging completion times."
