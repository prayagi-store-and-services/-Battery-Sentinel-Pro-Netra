package com.example.data.repository

/** App-owned disposable cache only. Constants are engineering limits, not Android guarantees. */
internal object CacheMaintenancePolicy {
    const val THRESHOLD_BYTES = 200L * 1024L * 1024L
    const val AUTO_INTERVAL_MS = 6L * 60L * 60L * 1000L
    /** Optional maintenance only; reuse central two-minute per-field freshness, never display fallbacks. */
    fun contextAllowsAutomaticWork(state: com.example.model.NetraCentralState?, now: Long): Boolean {
        val s = state ?: return false
        val level = s.batteryLevel ?: return false
        val temp = s.temperatureCelsius ?: return false
        if (level !in 15..100 || !temp.isFinite() || temp >= 40f) return false
        val f = s.fieldStates
        fun live(status: com.example.model.FieldStatus, at: Long) =
            status == com.example.model.FieldStatus.LIVE && at > 0L && at <= now && now - at <= 120_000L
        return live(f.levelStatus, f.levelObservedAt) && live(f.tempStatus, f.tempObservedAt)
    }
    fun overThreshold(bytes: Long) = bytes > THRESHOLD_BYTES
    fun due(now: Long, lastAttempt: Long): Boolean =
        lastAttempt <= 0L || (now >= lastAttempt && now - lastAttempt >= AUTO_INTERVAL_MS)
}
