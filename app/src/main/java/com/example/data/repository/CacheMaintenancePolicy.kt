package com.example.data.repository

/** App-owned disposable cache only. Constants are engineering limits, not Android guarantees. */
internal object CacheMaintenancePolicy {
    const val THRESHOLD_BYTES = 200L * 1024L * 1024L
    const val AUTO_INTERVAL_MS = 6L * 60L * 60L * 1000L
    fun overThreshold(bytes: Long) = bytes > THRESHOLD_BYTES
    fun due(now: Long, lastAttempt: Long): Boolean =
        lastAttempt <= 0L || (now >= lastAttempt && now - lastAttempt >= AUTO_INTERVAL_MS)
}
