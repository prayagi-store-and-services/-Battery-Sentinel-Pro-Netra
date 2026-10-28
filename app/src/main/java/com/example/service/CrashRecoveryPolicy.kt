package com.example.service

/** A crash is an uncaught Throwable, not an ordinary OS process death or rejected service start. */
internal data class CrashRecoveryState(val firstCrashMs: Long = 0, val crashes: Int = 0, val pauseUntilMs: Long = 0)
internal object CrashRecoveryPolicy {
    const val WINDOW_MS = 5L * 60 * 1000
    const val COOLDOWN_MS = 2L * 60 * 1000
    const val MAX_CRASHES = 3
    fun normalized(s: CrashRecoveryState, now: Long): CrashRecoveryState {
        if (now < s.firstCrashMs || s.pauseUntilMs > now + COOLDOWN_MS) return CrashRecoveryState()
        if (s.pauseUntilMs > 0 && now >= s.pauseUntilMs) return CrashRecoveryState()
        if (s.pauseUntilMs == 0L && now - s.firstCrashMs > WINDOW_MS) return CrashRecoveryState()
        return s
    }
    fun record(s: CrashRecoveryState, now: Long): CrashRecoveryState {
        val n = normalized(s, now)
        if (n.pauseUntilMs > now) return n // Do not keep extending an active pause.
        val count = (n.crashes + 1).coerceAtMost(MAX_CRASHES)
        return CrashRecoveryState(if (n.crashes == 0) now else n.firstCrashMs, count,
            if (count >= MAX_CRASHES) now + COOLDOWN_MS else 0)
    }
}
