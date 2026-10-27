package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalSilentPolicyTest {
    @Test fun automaticRunsStaySilent() = assertFalse(ThermalSilentPolicy.mayAnnounce(userTest = false))
    @Test fun criticalHeatMayWarnAutomatically() = assertTrue(ThermalSilentPolicy.mayAnnounce(userTest = false, criticalHeat = true))
    @Test fun userTestMaySpeak() = assertTrue(ThermalSilentPolicy.mayAnnounce(userTest = true))
}

class ThermalWarningDispatchTest {
    @Test fun automaticCriticalPolicyReachesControllerOutputsAndRespectsCooldown() {
        val policy = ThermalGuardianPolicy()
        val speech = mutableListOf<String>()
        val notifications = mutableListOf<String>()
        fun sample(now: Long, temp: Float) {
            val directives = policy.onSample(now, temp, true)
            directives.forEach { directive ->
                ThermalGuardianController.dispatchWarning(directive, false, policy.status().zone,
                    { text, _ -> speech.add(text) }, { title, _, _ -> notifications.add(title) })
            }
        }
        sample(0L, 45.2f)
        assertEquals(1, speech.size)
        assertEquals(1, notifications.size)
        assertTrue(speech.single().contains("Critical heat"))
        sample(5_000L, 44.8f)
        sample(10_000L, 45.1f)
        assertEquals(1, speech.size)
        assertEquals(1, notifications.size)
        sample(70_000L, 45.2f)
        assertEquals(2, speech.size)
    }

    @Test fun automaticNoncriticalControlsStaySilentButTestCanSpeak() {
        val policy = ThermalGuardianPolicy()
        var outputs = 0
        val directives = policy.onSample(0L, 42.5f, true)
        directives.forEach { ThermalGuardianController.dispatchWarning(it, false, policy.status().zone,
            { _, _ -> outputs++ }, { _, _, _ -> outputs++ }) }
        assertEquals(0, outputs)
        directives.forEach { ThermalGuardianController.dispatchWarning(it, true, policy.status().zone,
            { _, _ -> outputs++ }, { _, _, _ -> outputs++ }) }
        assertTrue(outputs > 0)
    }
}
