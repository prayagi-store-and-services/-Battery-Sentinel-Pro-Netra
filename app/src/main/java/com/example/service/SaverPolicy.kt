package com.example.service

enum class SaverDecision { APPLY, RESTORE, NONE }

/**
 * Pure rules for the Saver. No Android calls, so it is unit tested.
 * Starts when the battery is at or above the temperature limit, OR the level is at or below the level limit
 * while not charging. Stops (restores) only when both are back to normal with a small margin,
 * so it does not flip on and off around the limit.
 */
object SaverPolicy {
    const val DEFAULT_TEMP_C = 30f
    const val DEFAULT_LEVEL = 35
    const val TEMP_MARGIN_C = 2f
    const val LEVEL_MARGIN = 3

    fun decide(
        enabled: Boolean,
        active: Boolean,
        tempC: Float?,
        level: Int,
        charging: Boolean,
        tempLimitC: Float,
        levelLimit: Int,
        journeyOn: Boolean = false,
        reasonHeat: Boolean = true,
        reasonLow: Boolean = true,
        rapidRise: Boolean = false,
        reasonRise: Boolean = false,
        riseCalm: Boolean = true
    ): SaverDecision {
        if (!enabled && !journeyOn) return if (active) SaverDecision.RESTORE else SaverDecision.NONE
        val hot = tempC != null && tempC >= tempLimitC
        val low = !charging && (journeyOn || level <= levelLimit)
        // Restore as soon as the reason it started is gone: a heat start ends when the phone cooled down,
        // a low-battery start ends when the level recovered or charging began. A heat start must not stay
        // stuck just because the battery level is low.
        val tempOk = tempC == null || tempC <= tempLimitC - TEMP_MARGIN_C
        val levelOk = charging || (!journeyOn && level >= levelLimit + LEVEL_MARGIN)
        // A rapid-rise start ends only when the temperature has stopped rising (riseCalm) and is below the limit margin.
        val calm = (!reasonHeat || tempOk) && (!reasonLow || levelOk) && (!reasonRise || (riseCalm && tempOk))
        return when {
            !active && (hot || low || (rapidRise && !charging)) -> SaverDecision.APPLY
            active && calm -> SaverDecision.RESTORE
            else -> SaverDecision.NONE
        }
    }

    /** Journey mode lasts at most this long, then ends by itself. */
    const val JOURNEY_MAX_MS = 12L * 60 * 60 * 1000

    fun journeyActive(untilMs: Long, nowMs: Long): Boolean = untilMs > nowMs

    fun clampTemp(v: Float): Float = v.coerceIn(25f, 45f)
    fun clampLevel(v: Int): Int = v.coerceIn(5, 60)

    /** Apps we never close: the Netra apps, and these well known categories handled by the engine. */
    fun isNetraPackage(pkg: String): Boolean =
        pkg.startsWith("com.aistudio.") || pkg == "com.prayagi.netraeco" || pkg.startsWith("com.example.")
}

/**
 * Early start from the speed of the temperature rise, so the Saver does not wait for the limit.
 * Uses only real battery temperature samples. Rule: not charging, at least 3 samples, at least 90 seconds
 * between the coolest sample of the last 3 minutes and now, a rise of at least 1.2 C over that span
 * (so at least 0.4 C per minute), and a temperature of at least 33 C. Charging resets the window, so plugging in
 * never counts as a rise. Pure code, no Android calls.
 */
class RiseDetector {
    companion object {
        const val WINDOW_MS = 3 * 60_000L
        const val MIN_SPAN_MS = 90_000L
        const val MIN_GAP_MS = 20_000L
        const val STALE_MS = 2 * 60_000L
        const val MIN_RISE_C = 1.2f
        const val MIN_RATE_C_PER_MIN = 0.4f
        const val MIN_TEMP_C = 33f
        const val CALM_RATE_C_PER_MIN = 0.1f
    }

    private data class S(val t: Long, val c: Float)
    private val samples = ArrayDeque<S>()
    private var lastCharging: Boolean? = null

    fun reset() { samples.clear() }

    fun add(nowMs: Long, tempC: Float?, charging: Boolean) {
        if (lastCharging != null && lastCharging != charging) samples.clear()
        lastCharging = charging
        if (charging || tempC == null) { samples.clear(); return }
        if (samples.isNotEmpty() && nowMs - samples.last().t > STALE_MS) samples.clear()
        if (samples.isEmpty() || nowMs - samples.last().t >= MIN_GAP_MS) samples.addLast(S(nowMs, tempC))
        while (samples.isNotEmpty() && nowMs - samples.first().t > WINDOW_MS) samples.removeFirst()
    }

    /** Degrees C per minute from the coolest sample in the window to the latest, or null when there is not enough data. */
    fun ratePerMin(): Float? {
        if (samples.size < 3) return null
        val last = samples.last()
        val low = samples.minByOrNull { it.c } ?: return null
        val span = last.t - low.t
        if (span < MIN_SPAN_MS) return null
        return (last.c - low.c) / (span / 60_000f)
    }

    fun riseC(): Float? {
        if (samples.size < 3) return null
        return samples.last().c - (samples.minByOrNull { it.c }?.c ?: return null)
    }

    fun rapid(): Boolean {
        val rate = ratePerMin() ?: return false
        val last = samples.last()
        val rise = riseC() ?: return false
        return rise >= MIN_RISE_C && rate >= MIN_RATE_C_PER_MIN && last.c >= MIN_TEMP_C
    }

    /** True when there is no rise to speak of (flat or falling, or not enough data). */
    fun calm(): Boolean = (ratePerMin() ?: 0f) < CALM_RATE_C_PER_MIN
}
