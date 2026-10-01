package com.example.service

import com.example.model.NetraCentralState
import com.example.NetraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foundation for detecting and maintaining the phone's Ideal State.
 * Monitors canonical state to ensure device stays within efficiency parameters.
 */
class IdealStateEngine(private val application: NetraApplication) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        observeState()
    }

    private fun observeState() {
        engineScope.launch {
            application.centralDataCenter.centralState.collectLatest { state ->
                evaluateIdealState(state)
            }
        }
    }

    private fun evaluateIdealState(state: NetraCentralState) {
        // Ideal State: Screen Off, Low Temp (<= 30°C), Efficient CPU/RAM
        val isScreenOff = !state.isScreenOn && state.isScreenOffConfirmed
        val isTempIdeal = (state.temperatureCelsius ?: 99f) <= 30f
        val isResourcesIdeal = !state.isMemoryOptimizationNeeded && !state.isCpuOptimizationNeeded
        
        val isIdeal = isScreenOff && isTempIdeal && isResourcesIdeal
        
        if (isIdeal != state.isIdealStateActive || isTempIdeal != state.isIdealThermalTargetReached) {
            application.centralDataCenter.updateIdealStateStatus(isIdeal, isTempIdeal)
        }
    }
}
