package com.example.service

import android.content.ContentResolver
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import java.util.Calendar

/**
 * Active Power Saving: forced from 11 PM to 7 AM, and outside that window after the screen has been off for 15 minutes.
 * It changes only what Android lets an app change: screen timeout, brightness and auto-sync. Each value is saved first and
 * restored when the mode ends (brightness goes back to automatic). It cannot switch Wi-Fi, Bluetooth, mobile data or the
 * network type, so it can lower overnight drain but cannot promise a fixed figure.
 */
object ActivePowerSaver {
    private const val PREFS = "netra_sentinel_prefs"
    const val KEY_ENABLED = "active_saver_enabled"
    private const val KEY_APPLIED = "active_saver_applied"
    private const val KEY_TIMEOUT = "active_saver_prev_timeout"
    private const val KEY_SYNC = "active_saver_prev_sync"
    private const val KEY_OFF_SINCE = "active_saver_screen_off_since"
    const val IDLE_MS = 15L * 60L * 1000L
    private const val SAVER_TIMEOUT_MS = 15_000
    private const val SAVER_BRIGHTNESS = 10 // of 255

    /** 23:00 up to but not including 07:00. */
    fun inForcedWindow(hourOfDay: Int): Boolean = hourOfDay >= 23 || hourOfDay < 7

    fun shouldBeActive(enabled: Boolean, hourOfDay: Int, screenOn: Boolean, screenOffForMs: Long): Boolean =
        enabled && (inForcedWindow(hourOfDay) || (!screenOn && screenOffForMs >= IDLE_MS))

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, true)
    fun isApplied(c: Context) = prefs(c).getBoolean(KEY_APPLIED, false)
    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) restore(c)
    }

    private fun hourNow(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    fun onScreenOff(c: Context, nowMs: Long = System.currentTimeMillis()) {
        val p = prefs(c.applicationContext)
        if (p.getLong(KEY_OFF_SINCE, 0L) == 0L) p.edit().putLong(KEY_OFF_SINCE, nowMs).apply()
    }

    /** Screen on: leave the idle mode at once. Inside the 11 PM to 7 AM window the mode stays on. */
    fun onScreenOn(c: Context) {
        val app = c.applicationContext
        prefs(app).edit().putLong(KEY_OFF_SINCE, 0L).apply()
        if (!inForcedWindow(hourNow())) restore(app)
    }

    /** Called from the battery service on every update. */
    fun tick(c: Context, nowMs: Long = System.currentTimeMillis()) {
        val app = c.applicationContext
        val p = prefs(app)
        val screenOn = (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        if (screenOn) p.edit().putLong(KEY_OFF_SINCE, 0L).apply() else onScreenOff(app, nowMs)
        val since = p.getLong(KEY_OFF_SINCE, 0L)
        val offFor = if (since > 0L) nowMs - since else 0L
        val want = shouldBeActive(isEnabled(app), hourNow(), screenOn, offFor) && !DrivingFlag.isDriving(app)
        if (want && !isApplied(app)) apply(app) else if (!want && isApplied(app)) restore(app)
    }

    private fun apply(app: Context) {
        if (!Settings.System.canWrite(app) || ScreenOffSaver.isApplied(app)) return
        val p = prefs(app)
        val r = app.contentResolver
        runCatching {
            // Save first, so a failure half way can still be undone.
            p.edit().putBoolean(KEY_APPLIED, true)
                .putInt(KEY_TIMEOUT, Settings.System.getInt(r, Settings.System.SCREEN_OFF_TIMEOUT, 60000))
                .putBoolean(KEY_SYNC, ContentResolver.getMasterSyncAutomatically()).apply()
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, SAVER_BRIGHTNESS)
            Settings.System.putInt(r, Settings.System.SCREEN_OFF_TIMEOUT, SAVER_TIMEOUT_MS)
            ContentResolver.setMasterSyncAutomatically(false)
        }.onFailure { restore(app) }
    }

    fun restore(c: Context) {
        val app = c.applicationContext
        val p = prefs(app)
        if (!p.getBoolean(KEY_APPLIED, false)) return
        runCatching {
            val r = app.contentResolver
            if (Settings.System.canWrite(app)) {
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                Settings.System.putInt(r, Settings.System.SCREEN_OFF_TIMEOUT, p.getInt(KEY_TIMEOUT, 60000))
            }
            ContentResolver.setMasterSyncAutomatically(p.getBoolean(KEY_SYNC, true))
        }
        p.edit().putBoolean(KEY_APPLIED, false).apply()
    }

    fun statusText(c: Context): String = when {
        !Settings.System.canWrite(c) -> "Unavailable: allow Modify system settings"
        isApplied(c) -> "Active now (dimmed, 15 s timeout, auto-sync off)"
        isEnabled(c) -> "On, waiting: starts at 11 PM or 15 minutes after the screen goes off"
        else -> "Off"
    }
}
