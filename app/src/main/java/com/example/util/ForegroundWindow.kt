package com.example.util

/**
 * Foreground (screen) time per app inside one exact time window, from usage events.
 * Event types are the UsageEvents.Event constants: 1 resumed, 2 paused, 23 stopped,
 * 16 screen off (closes every open session). Open sessions end at the window end.
 * This is on-screen time, never battery use.
 */
object ForegroundWindow {
    data class Ev(val pkg: String, val type: Int, val timeMs: Long)

    const val RESUMED = 1
    const val PAUSED = 2
    const val SCREEN_OFF = 16
    const val STOPPED = 23

    fun compute(events: List<Ev>, startMs: Long, endMs: Long): Map<String, Long> {
        val open = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        fun close(pkg: String, at: Long) {
            val from = open.remove(pkg) ?: return
            val a = maxOf(from, startMs); val b = minOf(at, endMs)
            if (b > a) total[pkg] = (total[pkg] ?: 0L) + (b - a)
        }
        for (e in events.sortedBy { it.timeMs }) {
            if (e.timeMs > endMs) break
            when (e.type) {
                RESUMED -> if (e.pkg.isNotBlank() && !open.containsKey(e.pkg)) open[e.pkg] = e.timeMs
                PAUSED, STOPPED -> if (e.pkg.isNotBlank()) {
                    // a pause with no seen resume means it was already on screen when the window began
                    if (!open.containsKey(e.pkg) && e.type == PAUSED) open[e.pkg] = startMs
                    close(e.pkg, e.timeMs)
                }
                SCREEN_OFF -> for (p in open.keys.toList()) close(p, e.timeMs)
            }
        }
        for (p in open.keys.toList()) close(p, endMs)
        return total
    }
}
