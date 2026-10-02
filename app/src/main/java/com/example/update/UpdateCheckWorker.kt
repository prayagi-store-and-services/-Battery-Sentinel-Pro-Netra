package com.example.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pure helpers, unit tested. */
internal object UpdateNotifyPolicy {
    /** Notify once per new version code. */
    fun shouldNotify(lastNotifiedCode: Long, candidateCode: Long, installedCode: Long): Boolean =
        candidateCode > installedCode && candidateCode > lastNotifiedCode

    /** The release page for the tag inside ".../releases/download/<tag>/app-release.apk". */
    fun releasePageUrl(apkUrl: String): String? {
        val tag = apkUrl.substringBefore("/app-release.apk").substringAfterLast('/')
        return if (Regex("v[0-9]+\\.[0-9]+\\.[0-9]+").matches(tag)) {
            "https://github.com/${GitHubReleasePolicy.OWNER}/${GitHubReleasePolicy.REPO}/releases/tag/$tag"
        } else null
    }
}

/**
 * Once a day (network required, WorkManager decides the exact time, so it respects Doze and uses no exact alarms)
 * asks GitHub whether a newer stable release exists. If so, posts one status-bar notification per version.
 * Tapping it opens the release page in the browser. Nothing is downloaded or installed automatically.
 */
class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val conn = URL(GitHubReleasePolicy.API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Battery-Sentinel-Pro-Netra/" + BuildConfig.VERSION_NAME)
            val body = try {
                if (conn.responseCode !in 200..299) return@withContext Result.success()
                conn.inputStream.bufferedReader().use { it.readText().take(1024 * 1024) }
            } finally {
                conn.disconnect()
            }
            val candidate = GitHubReleasePolicy.parse(body, BuildConfig.VERSION_CODE.toLong())
            if (candidate != null) notifyIfNew(applicationContext, candidate)
        } catch (_: Exception) {
            // Try again at the next daily run; never hammer the API.
        }
        Result.success()
    }

    private fun notifyIfNew(c: Context, candidate: GitHubReleasePolicy.Candidate) {
        val prefs = c.getSharedPreferences("netra_release_update_cache", Context.MODE_PRIVATE)
        val last = prefs.getLong("notified_version_code", 0L)
        if (!UpdateNotifyPolicy.shouldNotify(last, candidate.versionCode, BuildConfig.VERSION_CODE.toLong())) return
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val page = UpdateNotifyPolicy.releasePageUrl(candidate.url) ?: return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            c, 0, Intent(Intent.ACTION_VIEW, Uri.parse(page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Naya version available hai")
            .setContentText("Battery Sentinel Pro Netra ka naya version aa gaya hai - tap karke download karein.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        prefs.edit().putLong("notified_version_code", candidate.versionCode).apply()
    }

    companion object {
        private const val CHANNEL_ID = "netra_app_update"
        private const val NOTIFICATION_ID = 7430
        private const val WORK_NAME = "netra_update_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
