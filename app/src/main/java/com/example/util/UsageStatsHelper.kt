package com.example.util

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.example.model.AppUsageItem
import java.util.Calendar

object UsageStatsHelper {

    fun getAppUsageDrainList(context: Context): List<AppUsageItem> {
        return try {
            if (!PermissionHelper.isUsageAccessGranted(context)) return emptyList()
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
            val packageManager = context.packageManager

            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis

            // Exact last-24-hours foreground time from usage events, clipped to the window. The old
            // INTERVAL_DAILY buckets each hold a whole calendar day, so adding them counted up to
            // two days under a "last 24 hours" label.
            val samples = mutableListOf<ForegroundWindow.Ev>()
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val ev = android.app.usage.UsageEvents.Event()
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(ev)
                samples.add(ForegroundWindow.Ev(ev.packageName ?: "", ev.eventType, ev.timeStamp))
            }
            val foregroundByPackage = ForegroundWindow.compute(samples, startTime, endTime)
            if (foregroundByPackage.isEmpty()) return emptyList()

            val results = mutableListOf<AppUsageItem>()

            for ((pkgName, foregroundMs) in foregroundByPackage) {
                if (foregroundMs > 30_000) { // at least 30 seconds
                    var appName = pkgName.substringAfterLast('.')
                    var category = "Tools & Utilities"

                    try {
                        val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                        appName = packageManager.getApplicationLabel(appInfo).toString()

                        category = when {
                            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> "Android System"
                            pkgName.contains("youtube", ignoreCase = true) || pkgName.contains("netflix", ignoreCase = true) || pkgName.contains("spotify", ignoreCase = true) -> "Media & Streaming"
                            pkgName.contains("chrome", ignoreCase = true) || pkgName.contains("browser", ignoreCase = true) -> "Web Browser"
                            pkgName.contains("instagram", ignoreCase = true) || pkgName.contains("tiktok", ignoreCase = true) || pkgName.contains("whatsapp", ignoreCase = true) || pkgName.contains("facebook", ignoreCase = true) -> "Social & Chat"
                            pkgName.contains("game", ignoreCase = true) || pkgName.contains("unity", ignoreCase = true) -> "Gaming"
                            else -> "Productivity & Apps"
                        }
                    } catch (_: PackageManager.NameNotFoundException) {}

                    val foregroundMinutes = foregroundMs / 60_000L

                    results.add(
                        AppUsageItem(
                            packageName = pkgName,
                            appName = appName,
                            foregroundTimeMinutes = foregroundMinutes,
                            category = category
                        )
                    )
                }
            }

            results.sortedByDescending { it.foregroundTimeMinutes }.take(20)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** UsageStats can return multiple daily buckets per package. Never turn time into energy. */
    internal fun aggregateForegroundTimes(samples: List<Pair<String, Long>>): Map<String, Long> =
        samples.filter { it.first.isNotBlank() && it.second > 0L }.groupBy { it.first }
            .mapValues { (_, values) -> values.fold(0L) { total, sample ->
                if (Long.MAX_VALUE - total < sample.second) Long.MAX_VALUE else total + sample.second
            } }

    fun openAppDetailsSettings(context: Context, packageName: String) {
        PermissionHelper.openAppDetailsSettings(context, packageName)
    }

    fun openUsageAccessSettings(context: Context) {
        PermissionHelper.openUsageAccessSettings(context)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        PermissionHelper.openBatteryOptimizationSettings(context)
    }
}
