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
    private val M = ChargingScreenGuard.Mode

    @Test fun modeOn_charging_unlockedOrLocked_allowed() {
        assertTrue(ChargingScreenGuard.holdAllowed(M.ON, ac, chg, false))
        assertTrue(ChargingScreenGuard.holdAllowed(M.ON, ac, chg, true))
    }
    @Test fun modeLock_onlyWhileUnlocked() {
        assertTrue(ChargingScreenGuard.holdAllowed(M.LOCK, ac, chg, false))
        assertFalse(ChargingScreenGuard.holdAllowed(M.LOCK, ac, chg, true))
    }
    @Test fun modeOff_neverHolds() {
        assertFalse(ChargingScreenGuard.holdAllowed(M.OFF, ac, chg, false))
        assertFalse(ChargingScreenGuard.holdAllowed(M.OFF, ac, chg, true))
    }
    @Test fun anyMode_notCharging_neverHolds() {
        for (m in M.values()) {
            assertFalse(ChargingScreenGuard.holdAllowed(m, 0, BatteryManager.BATTERY_STATUS_DISCHARGING, false))
            assertFalse(ChargingScreenGuard.holdAllowed(m, ac, BatteryManager.BATTERY_STATUS_NOT_CHARGING, false))
        }
    }
    @Test fun parse_defaultsToOn_andKnowsAllThree() {
        assertTrue(M.parse(null) == M.ON)
        assertTrue(M.parse("garbage") == M.ON)
        assertTrue(M.parse("LOCK") == M.LOCK)
        assertTrue(M.parse("OFF") == M.OFF)
    }
}
