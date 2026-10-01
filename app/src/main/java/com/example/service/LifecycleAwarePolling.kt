package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers

/**
 * Ultra-low power lifecycle & power state tracker.
 * Adapts engine behavior according to screen state and battery saver.
 */
class LifecycleTracker(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onStateChanged: (isScreenOn: Boolean, isPowerSaveMode: Boolean, isConfirmedOff: Boolean) -> Unit
) {
    private val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var isRegistered = false
    private var screenOffJob: Job? = null
    private var confirmedScreenOff = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isInteractive = powerManager?.isInteractive ?: true
            val isPowerSave = powerManager?.isPowerSaveMode ?: false

            if (isInteractive) {
                screenOffJob?.cancel()
                confirmedScreenOff = false
                onStateChanged(true, isPowerSave, false)
            } else {
                screenOffJob?.cancel()
                screenOffJob = scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    kotlinx.coroutines.delay(5000)
                    confirmedScreenOff = true
                    onStateChanged(false, isPowerSave, true)
                }
            }
        }
    }

    fun start() {
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            ctx.registerReceiver(screenReceiver, filter)
            isRegistered = true

            val isScreenOn = powerManager?.isInteractive ?: true
            val isPowerSave = powerManager?.isPowerSaveMode ?: false
            onStateChanged(isScreenOn, isPowerSave, !isScreenOn)
        }
    }

    fun stop() {
        if (isRegistered) {
            try {
                ctx.unregisterReceiver(screenReceiver)
            } catch (_: Exception) {}
            isRegistered = false
        }
    }

    fun isScreenInteractive(): Boolean = powerManager?.isInteractive ?: true
    fun isPowerSaveMode(): Boolean = powerManager?.isPowerSaveMode ?: false
}
