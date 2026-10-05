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
 * It can show over the lock screen (it does not unlock the phone). Real values only, tap to close.
 * It closes by itself when the charger is unplugged.
 */
class ChargingScreenActivity : ComponentActivity() {
    private var unplugReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        unplugReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { finish() }
        }
        registerReceiver(unplugReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED))
        setContent { ChargingScreenContent(onClose = { finish() }) }
    }

    override fun onDestroy() {
        unplugReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        unplugReceiver = null
        super.onDestroy()
    }
}
