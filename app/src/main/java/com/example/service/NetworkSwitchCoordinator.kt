package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.example.MainActivity
import com.example.util.PermissionHelper

/**
 * Screen-off network suggestion, radio time tracking and the experimental secure-setting write.
 * Android does not let a normal app change the network type; the suggestion asks the user to do it.
 */
object NetworkSwitchCoordinator {
    private const val TAG = "NetworkSwitchCoordinator"
    private const val PREFS = "netra_sentinel_prefs"
    private const val CHANNEL_ID = "netra_network_suggestion"
    private const val NOTIFICATION_ID = 7421
    const val EXTRA_OPEN_NETWORK_PANEL = "netra_open_network_panel"

    const val KEY_SUGGEST_ENABLED = "network_suggest_enabled"
    const val KEY_EXPERIMENT_ENABLED = "network_experiment_enabled"
    const val KEY_LAST_RESULT = "network_experiment_last_result"
    private const val KEY_ORIG_MODE = "network_experiment_orig_mode"
    private const val KEY_WRITTEN_MODE = "network_experiment_written_mode"
    private const val KEY_SETTING_NAME = "network_experiment_setting_name"
    private const val KEY_RADIO_LAST_TS = "radio_last_sample_ts"
    private const val KEY_RADIO_LAST_CLASS = "radio_last_sample_class"
    private const val MAX_SAMPLE_GAP_MS = 10L * 60L * 1000L

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPhoneStatePermission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun hasSecureSettingsPermission(c: Context): Boolean =
        c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    /** Current data network class, or UNKNOWN when it cannot be read. 5G NSA reports as LTE here. */
    fun currentRadioClass(c: Context): RadioClass {
        if (!hasPhoneStatePermission(c)) return RadioClass.UNKNOWN
        return try {
            val tm = c.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return RadioClass.UNKNOWN
            @Suppress("MissingPermission")
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> RadioClass.NR_5G
                TelephonyManager.NETWORK_TYPE_LTE -> RadioClass.LTE_4G
                TelephonyManager.NETWORK_TYPE_UNKNOWN -> RadioClass.UNKNOWN
                else -> RadioClass.THREE_G_OR_LOWER
            }
        } catch (e: Exception) {
            RadioClass.UNKNOWN
        }
    }

    /** Mobile bytes (rx+tx) in the heavy-use window, or null if unreadable (needs Usage Access, API 29+). */
    fun recentMobileBytes(c: Context): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (!PermissionHelper.isUsageAccessGranted(c)) return null
        return try {
            val nsm = c.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return null
            val end = System.currentTimeMillis()
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, end - NetworkDownswitchPolicy.HEAVY_DATA_WINDOW_MS, end)
            b.rxBytes + b.txBytes
        } catch (e: Exception) {
            null
        }
    }

    /** Adds time since the last sample to the previous network class. Called on screen events and the supervisor tick. */
    @Synchronized
    fun sampleRadioTime(c: Context) {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        val lastTs = p.getLong(KEY_RADIO_LAST_TS, 0L)
        val lastClass = p.getString(KEY_RADIO_LAST_CLASS, null)
        val editor = p.edit()
        if (lastTs > 0L && lastClass != null) {
            val gap = now - lastTs
            if (gap in 1..MAX_SAMPLE_GAP_MS) {
                editor.putLong("radio_ms_$lastClass", p.getLong("radio_ms_$lastClass", 0L) + gap)
            }
        }
        editor.putLong(KEY_RADIO_LAST_TS, now).putString(KEY_RADIO_LAST_CLASS, currentRadioClass(c).name).apply()
    }

    fun radioTimeMs(c: Context, cls: RadioClass): Long = prefs(c).getLong("radio_ms_${cls.name}", 0L)

    fun onScreenOff(c: Context) {
        val app = c.applicationContext
        sampleRadioTime(app)
        val p = prefs(app)
        val suggest = p.getBoolean(KEY_SUGGEST_ENABLED, false)
        val experiment = p.getBoolean(KEY_EXPERIMENT_ENABLED, false)
        if (!suggest && !experiment) return
        val decision = NetworkDownswitchPolicy.decide(currentRadioClass(app), recentMobileBytes(app))
        if (decision !is NetworkDownswitchPolicy.Decision.Suggest) {
            Log.d(TAG, "No switch: $decision")
            return
        }
        if (suggest) postSuggestion(app, decision)
        if (experiment) runExperiment(app)
    }

    fun onScreenOn(c: Context) {
        val app = c.applicationContext
        sampleRadioTime(app)
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIFICATION_ID)
        restoreExperimentIfPending(app)
    }

    private fun label(r: RadioClass) = when (r) {
        RadioClass.NR_5G -> "5G"
        RadioClass.LTE_4G -> "4G"
        RadioClass.THREE_G_OR_LOWER -> "3G"
        RadioClass.UNKNOWN -> "unknown"
    }

    private fun postSuggestion(c: Context, d: NetworkDownswitchPolicy.Decision.Suggest) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Network suggestions", NotificationManager.IMPORTANCE_LOW))
            }
            val open = Intent(c, MainActivity::class.java).putExtra(EXTRA_OPEN_NETWORK_PANEL, true)
            val pi = PendingIntent.getActivity(c, 7421, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = androidx.core.app.NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Switch to ${label(d.to)}?")
                .setContentText("Screen is off on ${label(d.from)}. Tap to open network settings. No heavy data use detected.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID, n)
        } catch (e: Exception) {
            Log.e(TAG, "Could not post suggestion", e)
        }
    }

    private fun settingName(c: Context): String {
        val subId = try { SubscriptionManager.getDefaultDataSubscriptionId() } catch (_: Exception) { -1 }
        return "preferred_network_mode" + if (subId >= 0) subId.toString() else ""
    }

    private fun setResult(c: Context, msg: String) {
        prefs(c).edit().putString(KEY_LAST_RESULT, msg).apply()
        Log.d(TAG, msg)
    }

    /** Experimental and unverified: Android may not apply this setting to the radio. Original value is always saved and restored. */
    private fun runExperiment(c: Context) {
        if (!hasSecureSettingsPermission(c)) { setResult(c, "Not run: WRITE_SECURE_SETTINGS not granted via ADB"); return }
        if (prefs(c).contains(KEY_ORIG_MODE)) { setResult(c, "Not run: a previous change is still waiting to be restored"); return }
        try {
            val name = settingName(c)
            val current = Settings.Global.getInt(c.contentResolver, name, Int.MIN_VALUE)
            if (current == Int.MIN_VALUE) { setResult(c, "Not run: setting $name not found, nothing changed"); return }
            val target = NetworkDownswitchPolicy.downMode(current)
            if (target == null) { setResult(c, "Not run: mode $current has no known one-level-down mapping, nothing changed"); return }
            prefs(c).edit().putInt(KEY_ORIG_MODE, current).putInt(KEY_WRITTEN_MODE, target).putString(KEY_SETTING_NAME, name).commit()
            val ok = Settings.Global.putInt(c.contentResolver, name, target)
            setResult(c, "Wrote $name $current -> $target (ok=$ok). Whether the radio actually changed is unverified.")
        } catch (e: Exception) {
            setResult(c, "Experiment failed: ${e.javaClass.simpleName}")
            restoreExperimentIfPending(c)
        }
    }

    /** Restores the saved original mode unless the user changed it in the meantime. Safe to call any time. */
    fun restoreExperimentIfPending(c: Context) {
        val p = prefs(c)
        if (!p.contains(KEY_ORIG_MODE)) return
        try {
            val name = p.getString(KEY_SETTING_NAME, null) ?: return
            val orig = p.getInt(KEY_ORIG_MODE, Int.MIN_VALUE)
            val written = p.getInt(KEY_WRITTEN_MODE, Int.MIN_VALUE)
            val now = Settings.Global.getInt(c.contentResolver, name, Int.MIN_VALUE)
            if (hasSecureSettingsPermission(c) && now == written && orig != Int.MIN_VALUE) {
                Settings.Global.putInt(c.contentResolver, name, orig)
                setResult(c, "Restored $name to $orig")
            }
            p.edit().remove(KEY_ORIG_MODE).remove(KEY_WRITTEN_MODE).remove(KEY_SETTING_NAME).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
        }
    }
}
