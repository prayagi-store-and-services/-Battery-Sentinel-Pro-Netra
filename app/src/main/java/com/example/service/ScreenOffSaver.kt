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
        if (!shouldApply(p.getBoolean(KEY_ENABLED, false), isCharging(app), canWriteSettings(app), p.getBoolean(KEY_APPLIED, false))) return
        try {
            val cr: ContentResolver = app.contentResolver
            val prevBrightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, -1)
            val prevMode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val prevSync = ContentResolver.getMasterSyncAutomatically()
            // Save first so a failure half way can still be undone.
            p.edit().putBoolean(KEY_APPLIED, true).putInt(KEY_BRIGHTNESS, prevBrightness).putInt(KEY_MODE, prevMode).putBoolean(KEY_SYNC, prevSync).apply()
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, LOW_BRIGHTNESS)
            ContentResolver.setMasterSyncAutomatically(false)
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply charging screen-off savings", e)
            restore(app)
        }
    }

    /** Puts back exactly what we saved. Does nothing if we did not change anything. */
    fun restore(c: Context) {
        val app = c.applicationContext
        val p = prefs(app)
        if (!p.getBoolean(KEY_APPLIED, false)) return
        try {
            val cr = app.contentResolver
            if (canWriteSettings(app)) {
                val b = p.getInt(KEY_BRIGHTNESS, -1)
                if (b >= 0) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, b)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt(KEY_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
            }
            ContentResolver.setMasterSyncAutomatically(p.getBoolean(KEY_SYNC, true))
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore settings", e)
        } finally {
            p.edit().putBoolean(KEY_APPLIED, false).apply()
        }
    }
}
