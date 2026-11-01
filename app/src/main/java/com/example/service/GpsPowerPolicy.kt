package com.example.service

/**
 * Pure decision logic for movement-triggered location power saving.
 *
 * Rule (owner spec): while the phone is moving, location (GPS) stays on.
 * When movement stops, it stays on for a short grace period, then turns off.
 * While the phone is still, location is switched on for a brief refresh pulse
 * about every few minutes so apps get a fresh fix.
 *
 * This class holds no Android dependencies so the timing rules are unit-tested
 * directly. The controller layer supplies the clock and applies the result.
 */
class GpsPowerPolicy(
    val offGraceMs: Long = DEFAULT_OFF_GRACE_MS,
    val stillPulseEveryMs: Long = DEFAULT_STILL_PULSE_EVERY_MS,
    val stillPulseOnMs: Long = DEFAULT_STILL_PULSE_ON_MS,
) {

    data class State(
        val lastMovementMs: Long,
        val lastPulseStartMs: Long?,
    )

    fun initialState(nowMs: Long): State = State(lastMovementMs = nowMs, lastPulseStartMs = null)

    /**
     * Returns the new state and whether location should be ON after this tick.
     * [movingNow] is the controller's movement reading for this tick.
     */
    fun tick(prev: State, nowMs: Long, movingNow: Boolean): Pair<State, Boolean> {
        if (movingNow) {
            return State(lastMovementMs = nowMs, lastPulseStartMs = null) to true
        }
        val sinceMovement = nowMs - prev.lastMovementMs
        if (sinceMovement < offGraceMs) {
            return prev.copy(lastPulseStartMs = null) to true
        }
        // Still beyond the grace period: pulse logic.
        val pulseStart = prev.lastPulseStartMs
        if (pulseStart != null) {
            return if (nowMs - pulseStart < stillPulseOnMs) {
                prev to true
            } else {
                // Pulse over; next pulse is due after the full interval from its start.
                prev.copy(lastPulseStartMs = null) to false
            }
        }
        // No active pulse: start one when the interval since the last activity elapsed.
        val lastActivity = maxOf(prev.lastMovementMs + offGraceMs, prev.lastPulseStartMs ?: Long.MIN_VALUE)
        return if (nowMs - lastActivity >= stillPulseEveryMs) {
            prev.copy(lastPulseStartMs = nowMs) to true
        } else {
            prev to false
        }
    }

    companion object {
        const val DEFAULT_OFF_GRACE_MS = 60_000L
        const val DEFAULT_STILL_PULSE_EVERY_MS = 5 * 60_000L
        const val DEFAULT_STILL_PULSE_ON_MS = 5_000L
    }
}
