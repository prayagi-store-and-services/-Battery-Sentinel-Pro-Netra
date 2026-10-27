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
    fun isEnabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, false)
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
        SavingSettings.retryPending(app)
        if (want && !isApplied(app)) apply(app) else if (!want) restore(app)
    }

    private fun apply(app: Context) {
        val display = SavingSettings.display(app, "active", SAVER_BRIGHTNESS, SAVER_TIMEOUT_MS)
        val sync = SavingSettings.pauseSync(app, "active")
        prefs(app).edit().putBoolean(KEY_APPLIED, display || sync).apply()
    }

    fun restore(c: Context) {
        SavingSettings.restore(c, "active")
        prefs(c).edit().putBoolean(KEY_APPLIED, false).apply()
    }

    fun statusText(c: Context): String = when {
        isApplied(c) -> SavingSettings.status(c)
        isEnabled(c) -> "On, waiting: starts at 11 PM or 15 minutes after the screen goes off"
        else -> "Off"
    }
}
