package com.example.ui.components

import android.os.BatteryManager

/**
 * Hard rule for the always-on charging screen: it may keep the display awake ONLY while the battery is
 * really charging right now. Charger unplugged, charging halted (for example by a charge limit), or any
 * other not-charging state means it must let go at once and the phone's own screen timeout takes over.
 * Pure function, fed from the live BatteryManager values (plug type and status), never a saved flag.
 */
object ChargingScreenGuard {
    fun keepAwakeAllowed(plugged: Int, status: Int): Boolean =
        plugged != 0 && status == BatteryManager.BATTERY_STATUS_CHARGING

    fun keepAwakeAllowed(batteryIntent: android.content.Intent?): Boolean =
        batteryIntent != null && keepAwakeAllowed(
            batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        )

    /** The three always-on modes the user picks. ON: comes on by itself when charging. LOCK: only while the phone is unlocked. OFF: never. */
    enum class Mode { ON, LOCK, OFF;
        companion object {
            const val KEY = "always_on_mode"
            fun parse(v: String?): Mode = values().firstOrNull { it.name == v } ?: ON
            fun load(c: android.content.Context): Mode = parse(c.getSharedPreferences(CHARGING_SCREEN_PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null))
        }
    }

    /**
     * Full rule. Charging must be live for any hold (the charging-stops rule), then the mode decides:
     * OFF never, LOCK only while the phone is unlocked, ON always.
     */
    fun holdAllowed(mode: Mode, plugged: Int, status: Int, phoneLocked: Boolean): Boolean =
        keepAwakeAllowed(plugged, status) && when (mode) {
            Mode.OFF -> false
            Mode.LOCK -> !phoneLocked
            Mode.ON -> true
        }

    fun holdAllowed(mode: Mode, batteryIntent: android.content.Intent?, phoneLocked: Boolean): Boolean =
        batteryIntent != null && holdAllowed(
            mode,
            batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
            phoneLocked
        )

    fun isPhoneLocked(c: android.content.Context): Boolean =
        (c.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isKeyguardLocked ?: false
}
