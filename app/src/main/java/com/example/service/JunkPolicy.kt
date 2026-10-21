package com.example.service

/** When to warn about phone-wide junk (cache of all apps). Pure decision logic, no Android types. */
object JunkPolicy {
    const val THRESHOLD_BYTES = 200L * 1024L * 1024L
    const val REWARN_AFTER_MS = 12L * 3600_000L
    const val REWARN_GROWTH_BYTES = 500L * 1024L * 1024L

    /** [totalBytes] null means the size could not be read: never warn from a guess. */
    fun shouldWarn(totalBytes: Long?, nowMs: Long, lastWarnAtMs: Long, lastWarnBytes: Long): Boolean {
        if (totalBytes == null || totalBytes < THRESHOLD_BYTES) return false
        if (lastWarnAtMs <= 0L) return true
        if (nowMs - lastWarnAtMs >= REWARN_AFTER_MS) return true
        return totalBytes >= lastWarnBytes + REWARN_GROWTH_BYTES
    }

    /** The warning goes away once the real reading is under the threshold. */
    fun shouldClear(totalBytes: Long?): Boolean = totalBytes != null && totalBytes < THRESHOLD_BYTES
}
