package com.example.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local preference-only gate installed before application initialization. No restart, alarm or network. */
object CrashRecoveryGate {
    private const val PREFS = "netra_crash_recovery"
    private var installed = false
    private val _pauseUntil = MutableStateFlow(0L)
    val pauseUntil: StateFlow<Long> = _pauseUntil.asStateFlow()
    private val _startFailures = MutableStateFlow<Set<String>>(emptySet())
    val startFailures: StateFlow<Set<String>> = _startFailures.asStateFlow()
    @Synchronized fun startResult(component: String, accepted: Boolean) {
        _startFailures.value = if (accepted) _startFailures.value - component else _startFailures.value + component
    }

    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        allowed(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { recordCrash(context) } catch (_: Throwable) { }
            try { previous?.uncaughtException(thread, error) } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun read(context: Context): CrashRecoveryState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return CrashRecoveryState(p.getLong("first_ms", 0), p.getInt("count", 0), p.getLong("pause_ms", 0))
    }
    private fun save(context: Context, s: CrashRecoveryState): Boolean = context.getSharedPreferences(PREFS, 0).edit()
        .putLong("first_ms", s.firstCrashMs).putInt("count", s.crashes).putLong("pause_ms", s.pauseUntilMs).commit()

    @Synchronized internal fun recordCrash(context: Context, now: Long = System.currentTimeMillis()) {
        val state = CrashRecoveryPolicy.record(read(context), now)
        save(context, state)
        _pauseUntil.value = state.pauseUntilMs
    }

    @Synchronized fun allowed(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val old = read(context)
        val state = CrashRecoveryPolicy.normalized(old, now)
        if (old != state) save(context, state)
        _pauseUntil.value = state.pauseUntilMs
        return state.pauseUntilMs <= now
    }

    /** Only the explicit visible Retry button clears the crash counter before cooldown expiry. */
    @Synchronized fun retry(context: Context): Boolean {
        if (!save(context, CrashRecoveryState())) return false
        _pauseUntil.value = 0
        return true
    }
}
