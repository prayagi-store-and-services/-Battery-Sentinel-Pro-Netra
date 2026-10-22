package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullChargeSilenceTest {
    @Test
    fun silentWindowAllowsNoSound() {
        assertFalse(FullChargeSilence.soundAllowed(nightProtectionActive = true))
    }

    @Test
    fun outsideWindowSoundIsAllowed() {
        assertTrue(FullChargeSilence.soundAllowed(nightProtectionActive = false))
    }
}
