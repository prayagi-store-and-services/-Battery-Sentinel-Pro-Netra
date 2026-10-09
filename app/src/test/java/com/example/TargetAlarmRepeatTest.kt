package com.example

import com.example.service.TargetAlarmRepeat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetAlarmRepeatTest {
    @Test fun notDueBeforeFirstAlarmFired() = assertFalse(TargetAlarmRepeat.due(0L, 500_000L, false))
    @Test fun notDueInsideInterval() = assertFalse(TargetAlarmRepeat.due(1_000L, 1_000L + 119_999L, false))
    @Test fun dueAfterTwoMinutes() = assertTrue(TargetAlarmRepeat.due(1_000L, 1_000L + 120_000L, false))
    @Test fun mutedNeverDue() = assertFalse(TargetAlarmRepeat.due(1_000L, 10_000_000L, true))
    @Test fun newSessionClearsMute() {
        TargetAlarmRepeat.muted = true
        TargetAlarmRepeat.resetForNewSession()
        assertFalse(TargetAlarmRepeat.muted)
    }
}
