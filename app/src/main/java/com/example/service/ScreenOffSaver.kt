package com.example.service

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log

/**
 * While the phone is charging AND the screen is off: lower the brightness and turn off auto-sync.
 * The previous values are saved and put back when the screen turns on or the charger is unplugged.
 * Both changes need permission: brightness needs "Modify system settings" (granted by the user in
 * Android settings). If it is not granted nothing is changed and the UI says so.
 * Off by default.
 */
object ScreenOffSaver {
    private const val TAG = "ScreenOffSaver"
    private const val PREFS = "netra_sentinel_prefs"
    const val KEY_ENABLED = "screen_off_saver_enabled"
    private const val KEY_APPLIED = "screen_off_saver_applied"
    private const val KEY_BRIGHTNESS = "screen_off_saver_prev_brightness"
    private const val KEY_MODE = "screen_off_saver_prev_mode"
    private const val KEY_SYNC = "screen_off_saver_prev_sync"
    private const val LOW_BRIGHTNESS = 10

    /** Pure decision, unit tested. */
    fun shouldApply(enabled: Boolean, charging: Boolean, canWrite: Boolean, alreadyApplied: Boolean): Boolean =
        enabled && charging && canWrite && !alreadyApplied

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isApplied(c: Context): Boolean = prefs(c).getBoolean(KEY_APPLIED, false)

    fun canWriteSettings(c: Context): Boolean = Settings.System.canWrite(c)

    fun isCharging(c: Context): Boolean {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    fun onScreenOff(c: Context) {
        val app = c.applicationContext
        val p = prefs(app)
        if (!shouldApply(p.getBoolean(KEY_ENABLED, false), isCharging(app), true, p.getBoolean(KEY_APPLIED, false))) return
        val display = SavingSettings.display(app, "charging_screen_off", LOW_BRIGHTNESS)
        val sync = SavingSettings.pauseSync(app, "charging_screen_off")
        p.edit().putBoolean(KEY_APPLIED, display || sync).apply()
    }

    /** Restores only this saver's changes; failures retain the persistent restore point. */
    fun restore(c: Context) {
        SavingSettings.restore(c, "charging_screen_off")
        prefs(c).edit().putBoolean(KEY_APPLIED, false).apply()
    }
}
