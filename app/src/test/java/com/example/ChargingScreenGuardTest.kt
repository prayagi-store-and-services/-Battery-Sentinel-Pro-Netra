package com.example

import android.os.BatteryManager
import com.example.ui.components.ChargingScreenGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingScreenGuardTest {
    private val ac = BatteryManager.BATTERY_PLUGGED_AC

    @Test fun chargingOnCharger_allowed() = assertTrue(ChargingScreenGuard.keepAwakeAllowed(ac, BatteryManager.BATTERY_STATUS_CHARGING))
    @Test fun unplugged_discharging_notAllowed() = assertFalse(ChargingScreenGuard.keepAwakeAllowed(0, BatteryManager.BATTERY_STATUS_DISCHARGING))
    @Test fun unplugged_evenIfStatusStillCharging_notAllowed() = assertFalse(ChargingScreenGuard.keepAwakeAllowed(0, BatteryManager.BATTERY_STATUS_CHARGING))
    @Test fun chargeLimitHalt_pluggedNotCharging_notAllowed() = assertFalse(ChargingScreenGuard.keepAwakeAllowed(ac, BatteryManager.BATTERY_STATUS_NOT_CHARGING))
    @Test fun unknownStatus_notAllowed() = assertFalse(ChargingScreenGuard.keepAwakeAllowed(ac, BatteryManager.BATTERY_STATUS_UNKNOWN))
    @Test fun full_notAllowed() = assertFalse(ChargingScreenGuard.keepAwakeAllowed(ac, BatteryManager.BATTERY_STATUS_FULL))
    @Test fun nullIntent_notAllowed() = assertFalse(ChargingScreenGuard.keepAwakeAllowed(null))
}
