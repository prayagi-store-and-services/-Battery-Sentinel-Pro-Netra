package com.example.service

/**
 * Rules for the opt-in battery percentage voice announcement. While the feature is on, the
 * discharging side always announces (every 5 percent by default, switchable to every 10 percent);
 * the charging side announces only when its own switch is on. Announcements fire only when the
 * level crosses a step boundary; nothing repeats and nothing catches up.
 */
object PercentAnnouncement {

    const val CATEGORY = "PERCENT_ANNOUNCEMENT"

    /** Step marks for a 5 or 10 percent cadence. 10% starts at 20 (20,30...100); 5% at 5. */
    fun steps(step: Int): List<Int> = when (step) {
        5 -> (5..100 step 5).toList()
        10 -> listOf(20, 30, 40, 50, 60, 70, 80, 90, 100)
        else -> emptyList()
    }

    /**
     * The single step mark crossed moving from [previous] to [current], or null when nothing was
     * crossed or the move is in the wrong direction. Charging counts upward moves, discharging
     * downward moves.
     */
    fun crossed(previous: Int?, current: Int?, charging: Boolean, step: Int): Int? {
        if (previous == null || current == null || previous == current) return null
        val marks = steps(step)
        return if (charging) {
            if (current > previous) marks.filter { it > previous && it <= current }.maxOrNull() else null
        } else {
            if (current < previous) marks.filter { it < previous && it >= current }.minOrNull() else null
        }
    }
}
