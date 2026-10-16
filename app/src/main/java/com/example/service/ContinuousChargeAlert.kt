package com.example.service

/**
 * Rules for the continuous high-charge unplug alert. At 80/85/90/95/100 percent while a charger is
 * attached, the spoken level alert and the phone's ringtone repeat until the charger is unplugged.
 * Unplugging is the only way to stop it: there is no dismiss and no snooze. All state is kept in
 * memory, so a reboot can never leave the alert stuck; it runs again only while the phone really is
 * connected at or above 80 percent.
 */
object ContinuousChargeAlert {

    /** Announcement category used for the repeating spoken line (exempt from the repeat-suppression ledger). */
    const val CATEGORY = "CONTINUOUS_CHARGE_ALERT"

    /** Fixed charge levels that start the continuous alert. */
    val ALERT_LEVELS: Set<Int> = setOf(80, 85, 90, 95, 100)

    /** Gap between two spoken repeats while the alert runs. */
    const val REPEAT_INTERVAL_MS = 10_000L

    /** Highest alert level at or below [level], or null when [level] is below every alert level. */
    fun milestoneFor(level: Int): Int? = ALERT_LEVELS.filter { level >= it }.maxOrNull()

    /**
     * True while the continuous alert should run: the feature is enabled, the level is known and at
     * or above the lowest alert level, and a charger is attached. Charging or only plugged in both
     * count (some phones pause charging near a limit); unknown counts as not attached, the same
     * rule as the target alert.
     */
    fun shouldAlert(level: Int?, isCharging: Boolean?, isConnected: Boolean?, enabled: Boolean): Boolean =
        enabled && level != null && milestoneFor(level) != null && (isCharging == true || isConnected == true)
}
