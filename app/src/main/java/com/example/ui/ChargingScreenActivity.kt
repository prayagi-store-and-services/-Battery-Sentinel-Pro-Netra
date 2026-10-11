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
            if (!G.holdAllowed(GMode.load(this), live, G.isPhoneLocked(this))) { finish(); return }
            if (intent?.getBooleanExtra(EXTRA_WAKE, false) == true) setTurnScreenOn(true)
        }
        unplugReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_POWER_DISCONNECTED || !G.keepAwakeAllowed(intent)) { finish(); return }
                if (intent.action == Intent.ACTION_SCREEN_OFF && GMode.load(context) != GMode.ON) finish()
                if (GMode.load(context) == GMode.OFF) finish()
            }
        }
        registerReceiver(unplugReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED).apply { addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_SCREEN_OFF) })
        setContent { ChargingScreenContent(onClose = { finish() }, keepScreenOn = false) }
    }

    companion object { const val EXTRA_WAKE = "wake_screen" }

    override fun onDestroy() {
        unplugReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        unplugReceiver = null
        super.onDestroy()
    }
}
