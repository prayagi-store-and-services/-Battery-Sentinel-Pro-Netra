package com.example.service

/**
 * Decides when the "target reached" alarm may repeat. Android has no public way for an app to stop charging, so the only
 * honest tool is to keep alerting until the charger is unplugged. The user can mute the repeats with "Dismiss Alarm";
 * the mute lasts until the charger is connected or disconnected again.
 */
object TargetAlarmRepeat {
    const val INTERVAL_MS = 120_000L

    @Volatile
    var muted: Boolean = false

    fun due(lastFiredAtMs: Long, nowMs: Long, isMuted: Boolean, intervalMs: Long = INTERVAL_MS): Boolean =
        !isMuted && lastFiredAtMs > 0L && nowMs - lastFiredAtMs >= intervalMs

    fun resetForNewSession() {
        muted = false
    }
}
