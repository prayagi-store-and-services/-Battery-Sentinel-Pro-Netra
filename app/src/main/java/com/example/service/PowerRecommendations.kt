package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.model.NetraCentralState

/** One silent recommendation per enabled level per discharge episode, retained across process death. */
object PowerRecommendations {
    val levels = listOf(50, 30, 20, 10, 5)
    const val CHANNEL = "netra_power_recommendations_v1"
    const val ID = 2020
    private fun prefs(c: Context) = c.getSharedPreferences("netra_power_recommendations_v1", Context.MODE_PRIVATE)
    fun enabled(c: Context, level: Int) = level in levels && prefs(c).getBoolean("enabled_$level", true)
    fun setEnabled(c: Context, level: Int, on: Boolean): Boolean {
        if (level !in levels) return false
        return prefs(c).edit().putBoolean("enabled_$level", on).commit()
    }

    /** A skipped sample emits only its most urgent level, never a burst of older recommendations. */
    fun nextLevel(level: Int?, fresh: Boolean, charging: Boolean?, connected: Boolean?, enabled: Set<Int>, seen: Set<Int>): Int? {
        if (!fresh || level == null || level !in 0..100 || charging != false || connected != false) return null
        return levels.filter { level <= it && it in enabled && it !in seen }.minOrNull()
    }

    @Synchronized fun onSample(c: Context, state: NetraCentralState) {
        val p = prefs(c)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!state.isDataFresh) return
        if (state.isChargerConnected == true) {
            if (p.getStringSet("seen", emptySet()).orEmpty().isNotEmpty()) {
                if (!p.edit().remove("seen").commit()) return
            }
            nm.cancel(ID)
            return
        }
        val storedSeen = p.getStringSet("seen", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        // If monitoring missed an entire charging episode, a rise of 5 percentage
        // points rearms only levels now safely above their hysteresis boundary.
        val seen = storedSeen.filter { state.batteryLevel == null || state.batteryLevel < it + 5 }.toSet()
        if (seen != storedSeen && !p.edit().putStringSet("seen", seen.map { it.toString() }.toSet()).commit()) return
        val level = nextLevel(state.batteryLevel, state.isDataFresh, state.isCharging, state.isChargerConnected,
            levels.filter { enabled(c, it) }.toSet(), seen) ?: return
        if (!com.example.NetraApplication.instance.settingsRepository.settings.value.notificationEnabled || !nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Power-saving recommendations", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Silent battery-level suggestions. Never changes settings automatically."
                setSound(null, null)
                enableVibration(false)
            })
            if (nm.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        }
        // Reserve before posting to prevent restart loops from repeatedly notifying. No system settings are written.
        val consumed = seen + levels.filter { it >= level }
        if (!p.edit().putStringSet("seen", consumed.map { it.toString() }.toSet()).commit()) return
        val body = "Battery ${state.batteryLevel}%. Consider lowering screen brightness and enabling Android Battery Saver. Netra has not changed either setting."
        fun action(action: String, request: Int): PendingIntent = PendingIntent.getActivity(c, request,
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Power-saving suggestion: ${state.batteryLevel}%")
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSilent(true).setOnlyAlertOnce(true).setAutoCancel(true)
            .setContentIntent(action(Settings.ACTION_BATTERY_SAVER_SETTINGS, 2020))
            .addAction(0, "Battery Saver", action(Settings.ACTION_BATTERY_SAVER_SETTINGS, 2021))
            .addAction(0, "Display settings", action(Settings.ACTION_DISPLAY_SETTINGS, 2022))
            .build()
        try { nm.notify(ID, notification) } catch (_: SecurityException) {
            // Permission can change after the check. Keep the reservation to avoid a retry loop.
        }
    }
}
