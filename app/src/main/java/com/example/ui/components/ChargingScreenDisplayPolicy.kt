package com.example.ui.components

import android.os.SystemClock
import com.example.model.CanonicalChargingSpeed
import com.example.model.FieldStatus
import com.example.model.NetraCentralState
import java.util.Calendar

/** Display-only rules. Does not change charging, lock the device, or sample hardware. */
internal class ChargingScreenDisplayPolicy {
    private var slowSince: Long? = null
    private var firstSlowSample: Long? = null
    private var session: Long? = null
    private var lastSlowSample: Long? = null
    private var blockedBySlow = false
    private var lastElapsed: Long? = null

    @Synchronized
    fun reset() {
        slowSince = null
        firstSlowSample = null
        session = null
        lastSlowSample = null
        blockedBySlow = false
        lastElapsed = null
    }

    @Synchronized
    fun allowed(state: NetraCentralState?, elapsedMs: Long, wallMs: Long, hour: Int): Boolean {
        if (lastElapsed?.let { elapsedMs < it } == true) reset()
        lastElapsed = elapsedMs
        if (state?.isChargerConnected == false && state.isCharging != true) reset()
        val identity = state?.chargerConnectedAt?.takeIf { it > 0L && it <= wallMs }
        if (identity != null && identity != session) {
            reset()
            session = identity
            lastElapsed = elapsedMs
        }
        val fields = state?.fieldStates
        val freshSpeed = state?.isDataFresh == true && state.isCharging == true &&
            state.isChargerConnected == true && state.lastUpdateTimestamp > 0L &&
            wallMs - state.lastUpdateTimestamp in 0..MAX_SAMPLE_AGE_MS &&
            fields?.powerStatus == FieldStatus.LIVE && fields.powerObservedAt == state.lastUpdateTimestamp &&
            state.chargingSpeed != CanonicalChargingSpeed.UNAVAILABLE
        if (freshSpeed) {
            if (state!!.chargingSpeed == CanonicalChargingSpeed.SLOW) {
                if (!blockedBySlow && lastSlowSample?.let {
                    state.lastUpdateTimestamp < it || state.lastUpdateTimestamp - it > MAX_SAMPLE_AGE_MS
                } == true) {
                    slowSince = null
                    firstSlowSample = null
                }
                lastSlowSample = state.lastUpdateTimestamp
                if (slowSince == null) {
                    slowSince = elapsedMs
                    firstSlowSample = state.lastUpdateTimestamp
                }
                // A second real sample must confirm persistent SLOW. UI clock ticks are not measurements.
                if (elapsedMs - requireNotNull(slowSince) >= SLOW_DURATION_MS &&
                    state.lastUpdateTimestamp > (firstSlowSample ?: Long.MAX_VALUE)) blockedBySlow = true
            } else {
                slowSince = null
                firstSlowSample = null
                lastSlowSample = null
                blockedBySlow = false
            }
        } else if (!blockedBySlow) {
            slowSince = null
            firstSlowSample = null
            lastSlowSample = null
        }
        return !night(hour) && !blockedBySlow
    }

    companion object {
        const val SLOW_DURATION_MS = 60_000L
        const val MAX_SAMPLE_AGE_MS = 90_000L
        fun night(hour: Int): Boolean = hour == 23 || hour in 0..5
    }
}

/** The existing service and displays consult one shared policy, never another telemetry engine. */
internal object ChargingScreenVisibility {
    private val policy = ChargingScreenDisplayPolicy()
    internal var wallClock: () -> Long = { System.currentTimeMillis() }
    internal var elapsedClock: () -> Long = { SystemClock.elapsedRealtime() }
    internal var stateSource: () -> NetraCentralState? = { currentState() }
    fun reset() = policy.reset()
    fun allowed(state: NetraCentralState? = stateSource()): Boolean {
        val now = wallClock()
        return policy.allowed(state, elapsedClock(), now,
            Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY))
    }
    internal fun currentState(): NetraCentralState? = try {
        com.example.NetraApplication.instance.centralDataCenter.centralState.value
    } catch (_: Exception) { null }
}
