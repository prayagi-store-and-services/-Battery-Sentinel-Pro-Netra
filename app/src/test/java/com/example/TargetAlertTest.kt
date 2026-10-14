package com.example

import com.example.service.TargetAlert
import org.junit.Assert.*
import org.junit.Test

class TargetAlertTest {
    @Test fun firesWhenChargingAtTarget() = assertTrue(TargetAlert.eligible(true, true, 80, 80))
    @Test fun firesWhenPluggedButPausedAtTarget() = assertTrue(TargetAlert.eligible(false, true, 85, 80))
    @Test fun firesWhenChargingStateUnknownButPlugged() = assertTrue(TargetAlert.eligible(null, true, 90, 90))
    @Test fun doesNotFireBelowTarget() = assertFalse(TargetAlert.eligible(true, true, 79, 80))
    @Test fun doesNotFireOnBattery() = assertFalse(TargetAlert.eligible(false, false, 95, 80))
    @Test fun doesNotFireWhenEverythingUnknown() = assertFalse(TargetAlert.eligible(null, null, 95, 80))
    @Test fun doesNotFireWithoutLevel() = assertFalse(TargetAlert.eligible(true, true, null, 80))
    @Test fun eachChipLevelFires() { for (t in listOf(80, 85, 90, 95, 100)) { assertFalse(TargetAlert.eligible(true, true, t - 1, t)); assertTrue(TargetAlert.eligible(true, true, t, t)) } }
    @Test fun statusOff() = assertEquals("Target alert is switched off.", TargetAlert.statusText(false, 80, 0L, -1, -1, ""))
    @Test fun statusArmed() { val s = TargetAlert.statusText(true, 85, 0L, -1, -1, ""); assertTrue(s.contains("armed at 85%")); assertFalse(s.contains("Last fired")) }
    @Test fun statusFired() { val s = TargetAlert.statusText(true, 85, 5L, 86, 85, "10:42"); assertTrue(s.contains("Last fired: 10:42 at 86% (target 85%)")) }
}
