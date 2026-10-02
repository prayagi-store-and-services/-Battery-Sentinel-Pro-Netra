package com.example

import com.example.service.ScreenOffSaver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOffSaverTest {
    @Test fun appliesOnlyWhenEnabledChargingAndPermitted() {
        assertTrue(ScreenOffSaver.shouldApply(enabled = true, charging = true, canWrite = true, alreadyApplied = false))
        assertFalse(ScreenOffSaver.shouldApply(false, true, true, false))
        assertFalse(ScreenOffSaver.shouldApply(true, false, true, false))
        assertFalse(ScreenOffSaver.shouldApply(true, true, false, false))
    }

    @Test fun neverAppliesTwiceSoPreviousValuesAreNotOverwritten() {
        assertFalse(ScreenOffSaver.shouldApply(true, true, true, true))
    }
}
