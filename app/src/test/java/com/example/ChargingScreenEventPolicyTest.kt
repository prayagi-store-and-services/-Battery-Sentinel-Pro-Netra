package com.example

import com.example.ui.components.ChargingScreenEventPolicy as P
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargingScreenEventPolicyTest {
    @Test fun screenOffInAlwaysOnDoesNotBecomeUnplugged() {
        // A SCREEN_OFF intent has no battery extras: both battery-derived booleans are false.
        assertEquals(P.Decision.KEEP, P.decide(P.Event.SCREEN_OFF, false, false, true))
    }
    @Test fun screenOffInLockModeClosesWithoutCallingDeviceLock() {
        assertEquals(P.Decision.CLOSE, P.decide(P.Event.SCREEN_OFF, false, false, false))
    }
    @Test fun actualUnplugStillClosesWithOptionalLock() {
        assertEquals(P.Decision.CLOSE_AND_LOCK_IF_ENABLED, P.decide(P.Event.UNPLUGGED, false, false, true))
    }
    @Test fun batteryNotChargingStillClosesWithOptionalLock() {
        assertEquals(P.Decision.CLOSE_AND_LOCK_IF_ENABLED, P.decide(P.Event.BATTERY_CHANGED, false, false, true))
    }
    @Test fun liveChargingKeepsScreenWhenModeAllowsIt() {
        assertEquals(P.Decision.KEEP, P.decide(P.Event.BATTERY_CHANGED, true, true, true))
    }
    @Test fun modeRestrictionClosesWithoutDeviceLock() {
        assertEquals(P.Decision.CLOSE, P.decide(P.Event.BATTERY_CHANGED, true, false, false))
    }
    @Test fun unrelatedBroadcastIsNotBatteryEvidence() {
        assertEquals(P.Decision.KEEP, P.decide(P.Event.OTHER, false, false, false))
    }
}
