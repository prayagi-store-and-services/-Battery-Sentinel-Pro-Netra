package com.example.service

import com.example.model.NetraCentralState

/** Reuses the Central Unit's persisted session identity. No additional preferences or timer store. */
object CanonicalSessionTime {
    fun startEpochMs(state: NetraCentralState, charging: Boolean): Long? = when {
        charging && state.isCharging == true && state.isChargerConnected == true -> state.chargingStartedAt
        !charging && state.isCharging == false && state.isChargerConnected == false -> state.dischargingStartedAt
        else -> null
    }

    fun validStart(startEpochMs: Long?, nowEpochMs: Long): Long? =
        startEpochMs?.takeIf { it > 0L && it <= nowEpochMs }

    /** May be negative after a reboot. Negative is a valid reconstructed monotonic start. */
    fun elapsedStart(startEpochMs: Long, nowEpochMs: Long, nowElapsedMs: Long): Long =
        nowElapsedMs - (nowEpochMs - startEpochMs)
}
