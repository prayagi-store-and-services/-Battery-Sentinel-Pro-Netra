package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalSilentPolicyTest {
    @Test fun automaticRunsStaySilent() = assertFalse(ThermalSilentPolicy.mayAnnounce(userTest = false))
    @Test fun userTestMaySpeak() = assertTrue(ThermalSilentPolicy.mayAnnounce(userTest = true))
}
