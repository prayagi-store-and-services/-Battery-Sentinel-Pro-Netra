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

    private val chg = BatteryManager.BATTERY_STATUS_CHARGING

    @Test fun modeOn_charging_unlockedOrLocked_allowed() {
        assertTrue(ChargingScreenGuard.holdAllowed(ChargingScreenGuard.Mode.ON, ac, chg, false))
        assertTrue(ChargingScreenGuard.holdAllowed(ChargingScreenGuard.Mode.ON, ac, chg, true))
    }
    @Test fun modeLock_onlyWhileUnlocked() {
        assertTrue(ChargingScreenGuard.holdAllowed(ChargingScreenGuard.Mode.LOCK, ac, chg, false))
        assertFalse(ChargingScreenGuard.holdAllowed(ChargingScreenGuard.Mode.LOCK, ac, chg, true))
    }
    @Test fun modeOff_neverHolds() {
        assertFalse(ChargingScreenGuard.holdAllowed(ChargingScreenGuard.Mode.OFF, ac, chg, false))
        assertFalse(ChargingScreenGuard.holdAllowed(ChargingScreenGuard.Mode.OFF, ac, chg, true))
    }
    @Test fun anyMode_notCharging_neverHolds() {
        for (m in ChargingScreenGuard.Mode.values()) {
            assertFalse(ChargingScreenGuard.holdAllowed(m, 0, BatteryManager.BATTERY_STATUS_DISCHARGING, false))
            assertFalse(ChargingScreenGuard.holdAllowed(m, ac, BatteryManager.BATTERY_STATUS_NOT_CHARGING, false))
        }
    }
    @Test fun parse_defaultsToOn_andKnowsAllThree() {
        assertTrue(ChargingScreenGuard.Mode.parse(null) == ChargingScreenGuard.Mode.ON)
        assertTrue(ChargingScreenGuard.Mode.parse("garbage") == ChargingScreenGuard.Mode.ON)
        assertTrue(ChargingScreenGuard.Mode.parse("LOCK") == ChargingScreenGuard.Mode.LOCK)
        assertTrue(ChargingScreenGuard.Mode.parse("OFF") == ChargingScreenGuard.Mode.OFF)
    }
}
