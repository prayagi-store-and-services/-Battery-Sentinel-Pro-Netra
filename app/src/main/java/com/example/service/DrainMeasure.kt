package com.example.service

/**
 * Honest battery drain measurement: battery percent at the start and now, over real elapsed time.
 * It says nothing about which app caused the drain (Android gives apps no per-app energy data) and the number is
 * only comparable with another run done under similar use (screen time, signal, brightness).
 */
object DrainMeasure {
    const val MIN_MINUTES = 10L

    sealed class Result {
        object NotStarted : Result()
        object TooShort : Result()
        data class Invalid(val reason: String) : Result()
        data class Ok(val dropPercent: Int, val minutes: Long, val percentPerHour: Float) : Result()
    }

    fun result(
        startLevel: Int, startMs: Long, startCharging: Boolean,
        nowLevel: Int, nowMs: Long, nowCharging: Boolean
    ): Result {
        if (startMs <= 0L || startLevel < 0) return Result.NotStarted
        if (startCharging || nowCharging) return Result.Invalid("The phone was charging, so drain cannot be measured.")
        if (nowLevel > startLevel) return Result.Invalid("The level went up, so the phone was charged in between.")
        val minutes = (nowMs - startMs) / 60_000L
        if (minutes < MIN_MINUTES) return Result.TooShort
        val drop = startLevel - nowLevel
        return Result.Ok(drop, minutes, drop * 60f / minutes)
    }
}
