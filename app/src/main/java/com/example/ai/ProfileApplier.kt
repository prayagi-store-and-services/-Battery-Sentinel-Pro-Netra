package com.example.ai

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.example.model.PowerProfileMode

/**
 * Applies the controls Android really lets this app change for a power profile, and puts them back.
 * Rule: save the user's value first, apply, restore when the profile ends. Brightness is restored to
 * automatic (adaptive) mode, not to a fixed number.
 */
object ProfileApplier {
    private const val P = "netra_profile_applied"
    data class Result(val brightness: String, val sync: String, val timeout: String)

    private fun prefs(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun apply(c: Context, mode: PowerProfileMode): Result {
        val saving = mode == PowerProfileMode.ENDURANCE || mode == PowerProfileMode.ULTRA_SAVER
        return if (saving) applySaving(c, mode) else restore(c)
    }

    private fun applySaving(c: Context, mode: PowerProfileMode): Result {
        val ultra = mode == PowerProfileMode.ULTRA_SAVER
        val p = prefs(c)
        val r = c.contentResolver
        val canWrite = Settings.System.canWrite(c)
        var brightness = "Unavailable: allow Modify system settings"
        var timeout = "Unavailable: allow Modify system settings"
        if (canWrite) {
            runCatching {
                if (!p.contains("prev_timeout")) {
                    p.edit().putInt("prev_timeout", Settings.System.getInt(r, Settings.System.SCREEN_OFF_TIMEOUT, 60000)).apply()
                }
                val level = if (ultra) 38 else 102 // of 255: 15% or 40%
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, level)
                brightness = "Applied: " + (level * 100 / 255) + "%"
                val ms = if (ultra) 15000 else 30000
                Settings.System.putInt(r, Settings.System.SCREEN_OFF_TIMEOUT, ms)
                timeout = "Applied: " + (ms / 1000) + " s"
            }.onFailure { brightness = "Unavailable on this phone"; timeout = "Unavailable on this phone" }
        }
        val sync = if (ultra) {
            runCatching {
                if (!p.contains("prev_sync")) p.edit().putBoolean("prev_sync", ContentResolver.getMasterSyncAutomatically()).apply()
                ContentResolver.setMasterSyncAutomatically(false)
                "Applied: auto-sync off"
            }.getOrDefault("Unavailable on this phone")
        } else "Not changed by this profile"
        return Result(brightness, sync, timeout)
    }

    private fun restore(c: Context): Result {
        val p = prefs(c)
        val r = c.contentResolver
        var brightness = "Not changed"
        var timeout = "Not changed"
        var sync = "Not changed"
        if (p.contains("prev_timeout") || p.contains("prev_sync") || p.getBoolean("dimmed", false)) {
            if (Settings.System.canWrite(c)) runCatching {
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                brightness = "Restored: automatic brightness"
                if (p.contains("prev_timeout")) {
                    Settings.System.putInt(r, Settings.System.SCREEN_OFF_TIMEOUT, p.getInt("prev_timeout", 60000))
                    timeout = "Restored: " + (p.getInt("prev_timeout", 60000) / 1000) + " s"
                }
            }
            if (p.contains("prev_sync")) runCatching {
                ContentResolver.setMasterSyncAutomatically(p.getBoolean("prev_sync", true))
                sync = "Restored: auto-sync " + (if (p.getBoolean("prev_sync", true)) "on" else "off (as before)")
            }
            p.edit().clear().apply()
        }
        return Result(brightness, sync, timeout)
    }
}
