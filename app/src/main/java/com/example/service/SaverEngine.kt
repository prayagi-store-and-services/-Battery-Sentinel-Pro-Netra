package com.example.service

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Saver: when the battery is too hot or too low, lower brightness to 10%, set the shortest screen
 * timeout, close background apps and clear notifications. Everything that was changed is put back
 * when the battery is back to normal. Off by default; every action has its own switch.
 *
 * Honest limits: Android only lets us end the background processes of other apps (not Force stop),
 * apps that run a foreground service (music, navigation, calls) are not affected, and apps may restart.
 * The result line shows the real free RAM before and after, which can be small.
 */
object SaverEngine {
    private const val TAG = "SaverEngine"
    const val PREFS = "netra_sentinel_prefs"
    const val KEY_ENABLED = "saver_enabled"
    const val KEY_TEMP = "saver_temp_c"
    const val KEY_LEVEL = "saver_level"
    const val KEY_ACT_DISPLAY = "saver_act_display"
    const val KEY_ACT_KILL = "saver_act_kill"
    const val KEY_ACT_NOTIF = "saver_act_notif"
    const val KEY_RESULT = "saver_last_result"
    private const val KEY_ACTIVE = "saver_active"
    private const val KEY_PREV_BRIGHTNESS = "saver_prev_brightness"
    private const val KEY_PREV_MODE = "saver_prev_mode"
    private const val KEY_PREV_TIMEOUT = "saver_prev_timeout"
    private const val KEY_DISPLAY_CHANGED = "saver_display_changed"
    private const val KEY_LAST_KILL = "saver_last_kill_ms"
    private const val LOW_BRIGHTNESS = 10
    private const val MIN_TIMEOUT_MS = 15_000
    private const val KILL_COOLDOWN_MS = 30 * 60 * 1000L

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default)

    fun isActive(c: Context): Boolean = prefs(c).getBoolean(KEY_ACTIVE, false)

    fun hasNotificationAccess(c: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    /** Called with every real battery reading. Cheap: a couple of comparisons. */
    fun onReading(context: Context, level: Int, tempC: Float?, charging: Boolean) {
        val app = context.applicationContext
        val p = prefs(app)
        val enabled = p.getBoolean(KEY_ENABLED, false)
        val active = p.getBoolean(KEY_ACTIVE, false)
        if (!enabled && !active) return
        val temp = tempC?.takeIf { it > 0f }
        val decision = SaverPolicy.decide(
            enabled, active, temp, level, charging,
            p.getFloat(KEY_TEMP, SaverPolicy.DEFAULT_TEMP_C), p.getInt(KEY_LEVEL, SaverPolicy.DEFAULT_LEVEL)
        )
        when (decision) {
            SaverDecision.APPLY -> apply(app)
            SaverDecision.RESTORE -> restore(app)
            SaverDecision.NONE -> {}
        }
    }

    private fun apply(app: Context) {
        val p = prefs(app)
        p.edit().putBoolean(KEY_ACTIVE, true).apply()
        val notes = mutableListOf<String>()
        if (p.getBoolean(KEY_ACT_DISPLAY, true)) {
            notes.add(applyDisplay(app, p))
        }
        if (p.getBoolean(KEY_ACT_NOTIF, true)) {
            notes.add(if (!hasNotificationAccess(app)) "notifications: access not granted"
            else if (SaverNotificationListener.clearAll()) "notifications cleared" else "notifications: listener not connected")
        }
        if (p.getBoolean(KEY_ACT_KILL, true)) {
            scope.launch { notes.add(closeBackgroundApps(app, p)); p.edit().putString(KEY_RESULT, "Last run: " + notes.joinToString("; ")).apply() }
        } else {
            p.edit().putString(KEY_RESULT, "Last run: " + notes.joinToString("; ")).apply()
        }
    }

    private fun applyDisplay(app: Context, p: android.content.SharedPreferences): String {
        if (!Settings.System.canWrite(app)) return "display: permission not granted"
        if (ScreenOffSaver.isApplied(app)) return "display: skipped (charging saver already active)"
        return try {
            val cr: ContentResolver = app.contentResolver
            p.edit()
                .putInt(KEY_PREV_BRIGHTNESS, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, -1))
                .putInt(KEY_PREV_MODE, Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
                .putInt(KEY_PREV_TIMEOUT, Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, -1))
                .putBoolean(KEY_DISPLAY_CHANGED, true)
                .apply()
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, LOW_BRIGHTNESS)
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, MIN_TIMEOUT_MS)
            "brightness 10% and 15 s screen timeout"
        } catch (e: Exception) {
            Log.w(TAG, "display change failed", e)
            restoreDisplay(app)
            "display: failed"
        }
    }

    private suspend fun closeBackgroundApps(app: Context, p: android.content.SharedPreferences): String {
        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST_KILL, 0L)
        if (now - last < KILL_COOLDOWN_MS) return "apps: skipped (ran less than 30 min ago)"
        return try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val before = freeRamMb(am)
            val pm = app.packageManager
            val protectedPkgs = protectedPackages(app)
            val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.distinct()
            var count = 0
            for (pkg in launchable) {
                if (pkg in protectedPkgs || SaverPolicy.isNetraPackage(pkg) || pkg == app.packageName) continue
                val flags = try { pm.getApplicationInfo(pkg, 0).flags } catch (e: Exception) { continue }
                if (flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                am.killBackgroundProcesses(pkg)
                count++
            }
            p.edit().putLong(KEY_LAST_KILL, now).apply()
            delay(2000)
            val after = freeRamMb(am)
            "asked Android to close background processes of $count apps; free RAM $before MB to $after MB"
        } catch (e: Exception) {
            Log.w(TAG, "background close failed", e)
            "apps: failed"
        }
    }

    private fun freeRamMb(am: ActivityManager): Long {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024 * 1024)
    }

    /** Never closed: the phone, SMS, launcher and keyboard in use, and common messaging apps. */
    private fun protectedPackages(app: Context): Set<String> {
        val set = mutableSetOf(
            "com.whatsapp", "org.telegram.messenger", "com.google.android.apps.messaging",
            "com.google.android.deskclock", "com.android.deskclock"
        )
        try {
            (app.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage?.let { set.add(it) }
        } catch (_: Exception) {}
        try { android.provider.Telephony.Sms.getDefaultSmsPackage(app)?.let { set.add(it) } } catch (_: Exception) {}
        try {
            app.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName?.let { set.add(it) }
        } catch (_: Exception) {}
        try {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.let { set.add(it) }
        } catch (_: Exception) {}
        return set
    }

    private fun restoreDisplay(app: Context) {
        val p = prefs(app)
        if (!p.getBoolean(KEY_DISPLAY_CHANGED, false)) return
        try {
            if (Settings.System.canWrite(app)) {
                val cr = app.contentResolver
                val b = p.getInt(KEY_PREV_BRIGHTNESS, -1)
                if (b >= 0) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, b)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt(KEY_PREV_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
                val t = p.getInt(KEY_PREV_TIMEOUT, -1)
                if (t > 0) Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, t)
            }
        } catch (e: Exception) {
            Log.w(TAG, "display restore failed", e)
        } finally {
            p.edit().putBoolean(KEY_DISPLAY_CHANGED, false).apply()
        }
    }

    /** Puts everything back. Closed apps and cleared notifications cannot be brought back; display settings are. */
    fun restore(app: Context) {
        val c = app.applicationContext
        restoreDisplay(c)
        prefs(c).edit().putBoolean(KEY_ACTIVE, false).apply()
    }
}
