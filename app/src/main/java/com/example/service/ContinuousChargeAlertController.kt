package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import com.example.NetraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the continuous high-charge unplug alert: the phone's ringtone loops and the spoken level
 * line repeats about every 10 seconds until the charger is unplugged. Unplugging is the only stop
 * (the service stopping also ends it). Nothing is persisted, so a reboot can never leave it stuck
 * on; after a restart it runs only while the phone really is connected at or above 80 percent.
 */
class ContinuousChargeAlertController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var ringPlayer: MediaPlayer? = null
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
            settings.continuousChargeAlertEnabled && settings.announcementsMasterEnabled
        )
        if (should && !running) {
            start()
        } else if (!should && running) {
            stop()
        }
    }

    private fun start() {
        running = true
        startRingtone()
        repeatJob = scope.launch {
            while (true) {
                val level = try {
                    NetraApplication.instance.centralDataCenter.centralState.value.batteryLevel
                } catch (_: Exception) { null }
                if (level != null) {
                    try {
                        NetraApplication.instance.announcementEngine.enqueue(
                            AnnouncementItem(
                                id = "continuous_charge_alert_${System.currentTimeMillis()}",
                                text = "$level percent. Phone ko charger se hatao.",
                                priority = AnnouncementPriority.CHARGER_STATE,
                                category = ContinuousChargeAlert.CATEGORY,
                                isNightException = true
                            )
                        )
                    } catch (_: Exception) {}
                }
                delay(ContinuousChargeAlert.REPEAT_INTERVAL_MS)
            }
        }
        Log.i(TAG, "Continuous charge alert started")
    }

    /** Stops the spoken repeats and the ringtone. Safe to call more than once. */
    @Synchronized
    fun stop() {
        if (!running && ringPlayer == null) return
        running = false
        repeatJob?.cancel()
        repeatJob = null
        stopRingtone()
        Log.i(TAG, "Continuous charge alert stopped")
    }

    private fun startRingtone() {
        stopRingtone()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
            ringPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ringtone loop could not start; spoken repeats continue without it", e)
            ringPlayer = null
        }
    }

    private fun stopRingtone() {
        try { ringPlayer?.stop() } catch (_: Exception) {}
        try { ringPlayer?.release() } catch (_: Exception) {}
        ringPlayer = null
    }

    companion object {
        private const val TAG = "NetraContinuousChargeAlert"
    }
}
