package com.example.service

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Phone-wide junk (cache of ALL apps) detection.
 *
 * Why the old check never saw 1-2 GB: it only measured this app's own cache folders. The cache of
 * every other app lives in their private folders, which Android hides from us. The one supported
 * way to read the total is StorageStatsManager.queryStatsForUser, which needs Usage Access.
 * Without Usage Access the size is reported as Unavailable, never guessed.
 *
 * Android does not let a normal app clear other apps' cache, so at 200 MB we warn once and offer
 * a button that opens Android's own storage screen.
 */
object JunkWatcher {
    data class Reading(val bytes: Long? = null, val reason: String? = null, val atMs: Long = 0L)

    private val _reading = MutableStateFlow(Reading())
    val reading: StateFlow<Reading> = _reading.asStateFlow()

    private const val CHANNEL = "junk_warning_channel"
    private const val NOTIF_ID = 42043
    private const val CHECK_EVERY_MS = 15 * 60_000L

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Total cache of all apps for this user, or null with a plain reason. */
    fun read(ctx: Context): Reading {
        val now = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Reading(null, "Needs Android 8 or newer", now)
        if (!hasUsageAccess(ctx)) return Reading(null, "Usage Access is off", now)
        return try {
            val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val stats = ssm.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())
            Reading(stats.cacheBytes, null, now)
        } catch (e: Exception) {
            Reading(null, "Android did not return the size", now)
        }
    }

    /** Tries Android's own storage screens, most specific first. Returns false if none opened. */
    fun openStorageSettings(ctx: Context): Boolean {
        val actions = listOf(
            Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            Settings.ACTION_MEMORY_CARD_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS
        )
        for (a in actions) {
            try {
                ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun openUsageAccessSettings(ctx: Context) {
        try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    /** Fresh reading for the screen (no warning, no throttle). */
    suspend fun refreshNow(ctx: Context) = withContext(Dispatchers.IO) { _reading.value = read(ctx) }

    /** Called from the monitor loop; does real work at most every 15 minutes. */
    suspend fun checkIfDue(ctx: Context) = withContext(Dispatchers.IO) {
        val prefs = ctx.getSharedPreferences("netra_junk_watch", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_check", 0L) < CHECK_EVERY_MS) return@withContext
        prefs.edit().putLong("last_check", now).apply()
        val r = read(ctx)
        _reading.value = r
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return@withContext
        if (JunkPolicy.shouldClear(r.bytes)) { nm.cancel(NOTIF_ID); return@withContext }
        val bytes = r.bytes ?: return@withContext
        if (!JunkPolicy.shouldWarn(bytes, now, prefs.getLong("warn_at", 0L), prefs.getLong("warn_bytes", 0L))) return@withContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Junk and cache warning", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            ctx, 104, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mb = bytes / (1024L * 1024L)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Junk and cache: $mb MB")
            .setContentText("Apps have stored $mb MB of cache (limit 200 MB). Tap to open storage settings and clear it.")
            .setContentIntent(open)
            .addAction(0, "Open storage settings", open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, n)
        prefs.edit().putLong("warn_at", now).putLong("warn_bytes", bytes).apply()
    }
}
