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
        reasonLow: Boolean = true
    ): SaverDecision {
        if (!enabled && !journeyOn) return if (active) SaverDecision.RESTORE else SaverDecision.NONE
        val hot = tempC != null && tempC >= tempLimitC
        val low = !charging && (journeyOn || level <= levelLimit)
        // Restore as soon as the reason it started is gone: a heat start ends when the phone cooled down,
        // a low-battery start ends when the level recovered or charging began. A heat start must not stay
        // stuck just because the battery level is low.
        val tempOk = tempC == null || tempC <= tempLimitC - TEMP_MARGIN_C
        val levelOk = charging || (!journeyOn && level >= levelLimit + LEVEL_MARGIN)
        val calm = (!reasonHeat || tempOk) && (!reasonLow || levelOk)
        return when {
            !active && (hot || low) -> SaverDecision.APPLY
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
