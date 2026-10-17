package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * "Who was active while the battery fell" analysis.
 *
 * Honest limits, shown to the user: Android does not give apps per-app battery energy (mAh or
 * percent) - only the phone's own battery screen has that. What Android DOES report (with Usage
 * access) is per-app ACTIVITY: time on screen and time with a running foreground service
 * (background work). An app that was active a lot during a fast battery drop is a suspect; an
 * app that was idle cannot be blamed from this data. Times are activity, never battery share.
 */
object DrainWindowAnalyzer {

    data class TimedEvent(val pkg: String, val type: Int, val ts: Long)
    data class Activity(val foregroundMs: Long, val backgroundServiceMs: Long)
    data class Suspect(
        val packageName: String,
        val appName: String,
        val foregroundMinutes: Long,
        val backgroundServiceMinutes: Long
    ) {
        val totalMinutes: Long get() = foregroundMinutes + backgroundServiceMinutes
    }

    /**
     * Pure interval aggregation so it can be unit tested. Open/close event pairs per package;
     * intervals clamp to [windowStartMs, windowEndMs]; an app still active at the window end
     * counts up to the end. Packages under one active minute are dropped as noise.
     */
    fun aggregate(events: List<TimedEvent>, windowStartMs: Long, windowEndMs: Long): Map<String, Activity> {
        if (windowEndMs <= windowStartMs) return emptyMap()
        val fgStart = mutableMapOf<String, Long>()
        val fgsStart = mutableMapOf<String, Long>()
        val fgMs = mutableMapOf<String, Long>()
        val fgsMs = mutableMapOf<String, Long>()

        fun closeInto(target: MutableMap<String, Long>, starts: MutableMap<String, Long>, pkg: String, end: Long) {
            val start = starts.remove(pkg) ?: return
            val s = maxOf(start, windowStartMs)
            val e = minOf(end, windowEndMs)
            if (e > s) target[pkg] = (target[pkg] ?: 0L) + (e - s)
        }

        for (ev in events.sortedBy { it.ts }) {
            when (ev.type) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> fgStart[ev.pkg] = ev.ts
                UsageEvents.Event.MOVE_TO_BACKGROUND -> closeInto(fgMs, fgStart, ev.pkg, ev.ts)
                UsageEvents.Event.ACTIVITY_STOPPED -> closeInto(fgMs, fgStart, ev.pkg, ev.ts)
                UsageEvents.Event.FOREGROUND_SERVICE_START -> fgsStart[ev.pkg] = ev.ts
                UsageEvents.Event.FOREGROUND_SERVICE_STOP -> closeInto(fgsMs, fgsStart, ev.pkg, ev.ts)
            }
        }
        fgStart.forEach { (pkg, start) ->
            val s = maxOf(start, windowStartMs)
            if (windowEndMs > s) fgMs[pkg] = (fgMs[pkg] ?: 0L) + (windowEndMs - s)
        }
        fgsStart.forEach { (pkg, start) ->
            val s = maxOf(start, windowStartMs)
            if (windowEndMs > s) fgsMs[pkg] = (fgsMs[pkg] ?: 0L) + (windowEndMs - s)
        }

        val out = mutableMapOf<String, Activity>()
        for (pkg in fgMs.keys + fgsMs.keys) {
            val a = Activity(fgMs[pkg] ?: 0L, fgsMs[pkg] ?: 0L)
            if (a.foregroundMs + a.backgroundServiceMs >= 60_000L) out[pkg] = a
        }
        return out
    }

    /** Reads usage events in [windowStartMs, windowEndMs]; empty when Usage access is off (never a guess). */
    fun readEvents(context: Context, windowStartMs: Long, windowEndMs: Long): List<TimedEvent> {
        if (!PermissionHelper.isUsageAccessGranted(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        return try {
            // Start earlier so an app already active at the window start is still counted (clamped).
            val events = usm.queryEvents(windowStartMs - 6 * 3600_000L, windowEndMs) ?: return emptyList()
            val e = UsageEvents.Event()
            val out = ArrayList<TimedEvent>(512)
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val type = e.eventType
                val wanted = type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    type == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        (type == UsageEvents.Event.ACTIVITY_STOPPED ||
                            type == UsageEvents.Event.FOREGROUND_SERVICE_START ||
                            type == UsageEvents.Event.FOREGROUND_SERVICE_STOP))
                val pkg = e.packageName
                if (wanted && !pkg.isNullOrBlank()) out.add(TimedEvent(pkg, type, e.timeStamp))
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Suspects for the window, most active first. The phone's own app is included and labelled honestly. */
    fun suspects(context: Context, windowStartMs: Long, windowEndMs: Long): List<Suspect> {
        val events = readEvents(context, windowStartMs, windowEndMs)
        if (events.isEmpty()) return emptyList()
        val pm = context.packageManager
        return aggregate(events, windowStartMs, windowEndMs).map { (pkg, a) ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg.substringAfterLast('.')
            }
            Suspect(
                packageName = pkg,
                appName = if (pkg == context.packageName) "$label (this app - always-on monitoring)" else label,
                foregroundMinutes = a.foregroundMs / 60_000L,
                backgroundServiceMinutes = a.backgroundServiceMs / 60_000L
            )
        }.sortedByDescending { it.totalMinutes }.take(15)
    }
}
