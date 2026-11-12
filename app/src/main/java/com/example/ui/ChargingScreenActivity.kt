package com.example.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ui.components.ChargingScreenContent
import com.example.ui.components.ChargingScreenEventPolicy as ScreenEvent

/**
 * Opened by the charger-connected event, or when the screen turns off while charging, when the user allowed it.
 * It can show over the lock screen (it does not unlock the phone). Real values only, double tap to close (the app that was open before comes back).
 * It closes by itself when the charger is unplugged.
 */
private typealias G = com.example.ui.components.ChargingScreenGuard
private typealias GMode = com.example.ui.components.ChargingScreenGuard.Mode

class ChargingScreenActivity : ComponentActivity() {
    private var unplugReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            val live = try { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Exception) { null }
            if (!G.holdAllowed(GMode.load(this), live, G.isPhoneLocked(this))) { closeChargingScreen(); return }
            if (intent?.getBooleanExtra(EXTRA_WAKE, false) == true) setTurnScreenOn(true)
        }
        unplugReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val event = when (intent.action) {
                    Intent.ACTION_POWER_DISCONNECTED -> ScreenEvent.Event.UNPLUGGED
                    Intent.ACTION_BATTERY_CHANGED -> ScreenEvent.Event.BATTERY_CHANGED
                    Intent.ACTION_SCREEN_OFF -> ScreenEvent.Event.SCREEN_OFF
                    else -> ScreenEvent.Event.OTHER
                }
                val mode = GMode.load(context)
                val decision = ScreenEvent.decide(
                    event = event,
                    chargingAllowed = G.keepAwakeAllowed(intent),
                    modeAllowsHold = G.holdAllowed(mode, intent, G.isPhoneLocked(context)),
                    alwaysOn = mode == GMode.ON
                )
                when (decision) {
                    ScreenEvent.Decision.KEEP -> Unit
                    ScreenEvent.Decision.CLOSE -> closeChargingScreen()
                    ScreenEvent.Decision.CLOSE_AND_LOCK_IF_ENABLED -> {
                        com.example.service.ScreenLock.lockIfAllowed(applicationContext, true)
                        closeChargingScreen()
                    }
                }
            }
        }
        registerReceiver(unplugReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED).apply { addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_SCREEN_OFF) })
        setContent { ChargingScreenContent(onClose = { closeChargingScreen() }, keepScreenOn = false) }
    }

    /** Close only this isolated task. Android resumes what was below; never launch the app. */
    internal fun closeChargingScreen() {
        if (isTaskRoot) finishAndRemoveTask() else finish()
    }

    companion object { const val EXTRA_WAKE = "wake_screen" }

    override fun onDestroy() {
        unplugReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        unplugReceiver = null
        super.onDestroy()
    }
}
