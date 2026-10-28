package com.example.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Metadata only, at most one network attempt per 24 hours across UI and worker. */
object AutomaticUpdateNotice {
    internal const val INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val PREFS = "eco_metadata_notice_v1"
    private const val CHANNEL = "eco_metadata_notice"
    private const val ID = 7435
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    internal fun due(now: Long, last: Long): Boolean = last == 0L || now < last || now - last >= INTERVAL_MS
    @Synchronized fun check(c: Context): AppUpdater.Newer? {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (due(now, p.getLong("attempt", 0))) {
            // Persist before networking: even failures do not create a tight retry loop.
            p.edit().putLong("attempt", now).commit()
            try {
                val result = AppUpdater.newer(c)
                p.edit().putLong("code", result?.code ?: 0).putString("name", result?.name ?: "").apply()
            } catch (_: Exception) { /* Keep last verified metadata; never claim current version on error. */ }
        }
        val code = p.getLong("code", 0)
        val name = p.getString("name", "").orEmpty()
        val result = if (code > AppUpdater.installedCode(c) && name.isNotBlank()) AppUpdater.Newer(code, name) else null
        if (result != null) notify(c, result) else NotificationManagerCompat.from(c).cancel(ID)
        return result
    }
    private fun notify(c: Context, result: AppUpdater.Newer) {
        val nm = NotificationManagerCompat.from(c)
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val p = prefs(c)
        if (p.getLong("notified", 0) >= result.code) return
        val intent = android.content.Intent(c, EcoUpdateRouteActivity::class.java)
        val manager = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        }
        try {
            val tap = PendingIntent.getActivity(c, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(ID, NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(c.applicationInfo.icon)
                .setContentTitle("Latest version available: ${result.name}")
                .setContentText("Update in Netra Ecosystem or on the Eco website.")
                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true).build())
            p.edit().putLong("notified", result.code).apply()
        } catch (_: SecurityException) { /* Denial never blocks in-app notices. */ }
    }
    fun wasDismissed(c: Context, code: Long) = prefs(c).getLong("dismissed", 0) >= code
    fun dismiss(c: Context, code: Long) { prefs(c).edit().putLong("dismissed", code).apply() }
    internal fun request() = PeriodicWorkRequestBuilder<EcoMetadataWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
    fun start(c: Context) {
        AppUpdater.retireLegacyUpdater(c)
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("eco_metadata_notice_v1", ExistingPeriodicWorkPolicy.KEEP, request())
    }
}
class EcoMetadataWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try { AutomaticUpdateNotice.check(applicationContext) } catch (_: Exception) { }
        Result.success()
    }
}

/** Internal notification tap: choose route at tap time and fall back if Eco cannot open. */
class EcoUpdateRouteActivity : android.app.Activity() {
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        if (!AppUpdater.openEco(this)) android.widget.Toast.makeText(this, "Could not open Eco or a browser.", android.widget.Toast.LENGTH_LONG).show()
        finish()
    }
}
