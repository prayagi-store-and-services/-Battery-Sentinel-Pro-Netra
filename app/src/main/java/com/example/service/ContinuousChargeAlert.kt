package com.example.service

/**
 * Rules for the continuous unplug alert at the user's charge target. When the battery reaches the
 * user-selected target (80/85/90/95/100 percent) while a charger is attached, the spoken level
 * alert and the phone's ringtone repeat until the charger is unplugged. The alert never fires
 * below the target: the repeat steps are the fixed levels at or above the target, so a 95 percent
 * target stays silent at 80, 85 and 90. Unplugging is the only way to stop it: there is no dismiss
 * and no snooze. All state is kept in memory, so a reboot can never leave the alert stuck; it runs
 * again only while the phone really is connected at or above the target.
 */
object ContinuousChargeAlert {

    /** Announcement category used for the repeating spoken line (exempt from the repeat-suppression ledger). */
    const val CATEGORY = "CONTINUOUS_CHARGE_ALERT"

    /** Fixed charge steps the repeating alert can start from; the user's target picks the first one. */
    val ALERT_LEVELS: Set<Int> = setOf(80, 85, 90, 95, 100)

    /** Gap between two spoken repeats while the alert runs. */
    const val REPEAT_INTERVAL_MS = 10_000L

    /** Highest alert step reached at [level] for target [targetPercent], or null below the target. */
    fun milestoneFor(level: Int, targetPercent: Int): Int? =
        ALERT_LEVELS.filter { it >= targetPercent && level >= it }.maxOrNull()

    /**
     * True while the continuous alert should run: the feature is enabled, the level is known and at
     * or above the user's target, at least one repeat step exists at or above the target, and a
     * charger is attached. Charging or only plugged in both count (some phones pause charging near
     * a limit); unknown counts as not attached, the same rule as the target alert. Below the target
     * it never alerts, no matter the charging state.
     */
    fun shouldAlert(level: Int?, isCharging: Boolean?, isConnected: Boolean?, enabled: Boolean, targetPercent: Int): Boolean =
        enabled && level != null && level >= targetPercent && milestoneFor(level, targetPercent) != null && (isCharging == true || isConnected == true)
}
