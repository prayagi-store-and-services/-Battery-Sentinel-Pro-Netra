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
}
