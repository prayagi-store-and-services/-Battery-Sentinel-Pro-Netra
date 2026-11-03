package com.example.service

import android.content.Context
import android.util.Log
import com.example.NetraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the continuous unplug alert at the user's charge target: once the battery reaches the
 * user-selected target the spoken level line repeats about every 10 seconds until the charger is
 * unplugged. Voice only: no ringtone (ringtone was removed because users, especially seniors,
 * switched the whole feature off to stop the noise). It never starts below the target. Unplugging
 * is the only stop (the service stopping also ends it). Nothing is persisted, so a reboot can
 * never leave it stuck on; after a restart it runs only while the phone really is connected at or
 * above the target.
 */
class ContinuousChargeAlertController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var repeatJob: Job? = null
    private var running = false

    /** Call with every central state update. Starts, keeps, or stops the alert as the state requires. */
    @Synchronized
    fun onState(level: Int?, isCharging: Boolean?, isConnected: Boolean?) {
        val settings = try {
            NetraApplication.instance.settingsRepository.settings.value
        } catch (_: Exception) { null } ?: return
        val should = ContinuousChargeAlert.shouldAlert(
            level, isCharging, isConnected,
            settings.continuousChargeAlertEnabled && settings.announcementsMasterEnabled,
            settings.chargeTargetPercent
        )
        if (should && !running) {
            start()
        } else if (!should && running) {
            stop()
        }
    }

    /** True while the silent (Night Protection) window is active. Re-read from the clock every call. */
    private fun silentWindowActive(): Boolean = try {
        val dataCenter = NetraApplication.instance.centralDataCenter
        dataCenter.refreshNightProtectionStateSynchronously()
        !FullChargeSilence.soundAllowed(dataCenter.centralState.value.isNightProtectionActive)
    } catch (_: Exception) { false }

    private fun start() {
        running = true
        repeatJob = scope.launch {
            while (true) {
                val level = try {
                    NetraApplication.instance.centralDataCenter.centralState.value.batteryLevel
                } catch (_: Exception) { null }
                if (silentWindowActive()) {
                    // Silent window: no spoken repeat. Resumes only after the window ends.
                } else if (level != null) {
                    try {
                        NetraApplication.instance.announcementEngine.enqueue(
                            AnnouncementItem(
                                id = "continuous_charge_alert_${System.currentTimeMillis()}",
                                text = "$level percent. Phone ko charger se hatao.",
                                priority = AnnouncementPriority.CHARGER_STATE,
                                category = ContinuousChargeAlert.CATEGORY,
                                isNightException = false
                            )
                        )
                    } catch (_: Exception) {}
                }
                delay(ContinuousChargeAlert.REPEAT_INTERVAL_MS)
            }
        }
        Log.i(TAG, "Continuous charge alert started")
    }

    /** Stops the spoken repeats. Safe to call more than once. */
    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        repeatJob?.cancel()
        repeatJob = null
        Log.i(TAG, "Continuous charge alert stopped")
    }

    companion object {
        private const val TAG = "NetraContinuousChargeAlert"
    }
}
