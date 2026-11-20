package com.example.service

import com.example.model.FieldStatus
import com.example.model.NetraCentralState

/**
 * Read-only translation of the existing central owner's snapshot. Not a second state owner.
 * The central wall-clock timestamp is NOT relabelled as monotonic acquisition provenance.
 * A future consumer must provide genuine matching monotonic provenance and freshness evidence.
 * This adapter is not called by any production consumer in Slice1.
 */
internal object CentralPowerWorkContext {
    fun fromSnapshot(
        state: NetraCentralState,
        observedAtElapsedMillis: Long?,
        nowElapsedMillis: Long,
        acquisitionFresh: Boolean,
        inDoze: Boolean?,
        appVisible: Boolean,
    ): PowerWorkContext = PowerWorkContext(
        batteryLevelAvailable = state.batteryLevel?.let { it in 0..100 } == true &&
            state.fieldStates.levelStatus == FieldStatus.LIVE,
        stateFresh = state.isDataFresh && acquisitionFresh,
        observedAtElapsedMillis = observedAtElapsedMillis,
        nowElapsedMillis = nowElapsedMillis,
        existingRestrictionActive = state.isCriticalThermalActive || state.isLowBatteryControlActive,
        inDoze = inDoze,
        appVisible = appVisible,
        screenOn = state.isScreenOn && !state.isScreenOffConfirmed,
    )
}
