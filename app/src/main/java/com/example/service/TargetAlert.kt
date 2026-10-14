package com.example.service

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Charge target alert rules. The alert must fire when the battery reaches the chosen level while the charger is connected,
 * also when the phone reports "not charging" at that level (many phones pause charging near a limit). Pure rules plus a visible
 * status and a test, so a silent failure can be seen instead of guessed.
 */
object TargetAlert {
    /** Level reached and a charger is attached. Charging or plugged-in both count; unknown state counts as not attached. */
    fun eligible(charging: Boolean?, connected: Boolean?, level: Int?, target: Int): Boolean =
        level != null && level >= target && (charging == true || connected == true)

    @Volatile var lastFiredAtMs: Long = 0L
    @Volatile var lastFiredLevel: Int = -1
    @Volatile var lastFiredTarget: Int = -1

    fun markFired(nowMs: Long, level: Int, target: Int) {
        lastFiredAtMs = nowMs; lastFiredLevel = level; lastFiredTarget = target
    }

    fun statusText(enabled: Boolean, target: Int, firedAtMs: Long, firedLevel: Int, firedTarget: Int, timeText: String): String {
        if (!enabled) return "Target alert is switched off."
        val armed = "Target alert is armed at $target%. It fires when the battery reaches $target% with the charger connected."
        return if (firedAtMs > 0L) "$armed Last fired: $timeText at $firedLevel% (target $firedTarget%), since the app last started." else armed
    }

    /** Sends a test notification and a test voice line. Returns what really happened, in plain words. */
    fun sendTest(c: Context): String {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return "Notifications are off for this app in Android settings, so the alert cannot show. Turn them on."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = nm.getNotificationChannel(BatteryMonitorService.CHANNEL_ALERTS_ID)
            if (ch == null) return "The alerts channel does not exist yet. Open the app again and retry."
            if (ch.importance == NotificationManager.IMPORTANCE_NONE) return "The Alerts notification channel is switched off in Android settings. Turn it on."
        }
        val n = NotificationCompat.Builder(c, BatteryMonitorService.CHANNEL_ALERTS_ID)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setContentTitle("Test: charge target alert")
            .setContentText("If you can see this, the target alert can reach you.")
            .setAutoCancel(true)
            .build()
        nm.notify(2003, n)
        val master = try { com.example.NetraApplication.instance.settingsRepository.settings.value.announcementsMasterEnabled } catch (_: Exception) { true }
        if (!master) return "Test notification sent. Voice announcements are switched off, so there is no spoken alert."
        try {
            com.example.NetraApplication.instance.announcementEngine.enqueue(
                AnnouncementItem(
                    id = "charge_target_test_${System.currentTimeMillis()}",
                    text = "Test. Target alert is working.",
                    priority = AnnouncementPriority.CHARGER_STATE,
                    category = "CHARGE_TARGET",
                    isNightException = true
                )
            )
        } catch (_: Exception) { return "Test notification sent. The voice line could not be queued." }
        return "Test notification and voice line sent."
    }
}
