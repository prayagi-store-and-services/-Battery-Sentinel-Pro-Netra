package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Apps used in the last minutes while the phone is warm. Android gives no heat reading per app,
 * so this is an estimate from foreground time only. Background-only heating cannot be seen here.
 */
data class HeatSuspect(val packageName: String, val appName: String, val foregroundMinutes: Long)

object HeatSuspects {
    const val HOT_CELSIUS = 40f
    const val WINDOW_MS = 30 * 60_000L
    private const val MIN_MS = 60_000L
    private const val FG = 1  // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
    private const val BG = 2  // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED

    fun isHot(tempCelsius: Float?): Boolean = tempCelsius != null && tempCelsius >= HOT_CELSIUS

    /** events: (package, type, timeMs) sorted by time. Returns foreground ms per package inside the window. */
    internal fun foregroundMs(events: List<Triple<String, Int, Long>>, start: Long, end: Long): Map<String, Long> {
        val total = HashMap<String, Long>()
        val openSince = HashMap<String, Long>()
        for ((pkg, type, t) in events.sortedBy { it.third }) {
            if (pkg.isBlank() || t < start || t > end) continue
            when (type) {
                FG -> if (!openSince.containsKey(pkg)) openSince[pkg] = t
                BG -> {
                    val from = openSince.remove(pkg) ?: start
                    if (t > from) total[pkg] = (total[pkg] ?: 0L) + (t - from)
                }
            }
        }
        for ((pkg, from) in openSince) if (end > from) total[pkg] = (total[pkg] ?: 0L) + (end - from)
        return total
    }

    internal fun rank(ms: Map<String, Long>, exclude: Set<String>, limit: Int = 3): List<Pair<String, Long>> =
        ms.filter { it.value >= MIN_MS && it.key !in exclude }.entries
            .sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    fun find(context: Context, tempCelsius: Float?, now: Long = System.currentTimeMillis()): List<HeatSuspect> {
        if (!isHot(tempCelsius)) return emptyList()
        return try {
            if (!PermissionHelper.isUsageAccessGranted(context)) return emptyList()
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
            val start = now - WINDOW_MS
            val ev = usm.queryEvents(start, now) ?: return emptyList()
            val list = ArrayList<Triple<String, Int, Long>>()
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType == FG || e.eventType == BG) list.add(Triple(e.packageName ?: "", e.eventType, e.timeStamp))
            }
            val pm = context.packageManager
            rank(foregroundMs(list, start, now), setOf(context.packageName)).map { (pkg, ms) ->
                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: PackageManager.NameNotFoundException) { pkg }
                HeatSuspect(pkg, name, ms / 60_000L)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
