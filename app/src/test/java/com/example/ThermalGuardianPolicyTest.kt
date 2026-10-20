package com.example

import com.example.service.ThermalGuardianPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalGuardianPolicyTest {

    @Test
    fun `cool phone takes no action`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, 28.5f, false)
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.ApplyLightControls })
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.Speak })
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.SampleGap })
        assertFalse(p.controlsApplied)
    }

    @Test
    fun `monitor zone only watches`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, 36f, false)
        assertEquals(ThermalGuardianPolicy.Zone.MONITOR, p.zone)
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.ApplyLightControls })
    }

    @Test
    fun `light zone dims screen once`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, 38.5f, false)
        assertEquals(ThermalGuardianPolicy.Zone.LIGHT, p.zone)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.ApplyLightControls })
        assertTrue(p.controlsApplied)
        // staying warm must not re-apply
        val d2 = p.onSample(15_000L, 38.7f, false)
        assertTrue(d2.none { it is ThermalGuardianPolicy.Directive.ApplyLightControls })
    }

    @Test
    fun `active zone applies controls, notifies and starts observe phase`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, 40.5f, true)
        assertEquals(ThermalGuardianPolicy.Zone.ACTIVE, p.zone)
        assertEquals(ThermalGuardianPolicy.Phase.OBSERVE, p.phase)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.ApplyActiveControls })
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.Notify })
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.Speak })
    }

    @Test
    fun `identify phase asks for heavy apps after 30 seconds`() {
        val p = ThermalGuardianPolicy()
        p.onSample(0L, 40.5f, false)
        val d = p.onSample(31_000L, 40.6f, false)
        assertEquals(ThermalGuardianPolicy.Phase.IDENTIFY, p.phase)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.IdentifyHeavyApps })
    }

    @Test
    fun `severe speaks immediately and repeats with cooldown`() {
        val p = ThermalGuardianPolicy()
        val d1 = p.onSample(0L, 42.5f, true)
        assertTrue(d1.any { it is ThermalGuardianPolicy.Directive.ApplySevereControls })
        assertEquals(1, d1.count { it is ThermalGuardianPolicy.Directive.Speak })
        // 30s later: cooldown not over, no repeat
        val d2 = p.onSample(30_000L, 42.8f, true)
        assertEquals(0, d2.count { it is ThermalGuardianPolicy.Directive.Speak })
        // 61s later: repeats
        val d3 = p.onSample(61_000L, 42.9f, true)
        assertEquals(1, d3.count { it is ThermalGuardianPolicy.Directive.Speak })
    }

    @Test
    fun `recovery waits for sustained cool then restores only once`() {
        val p = ThermalGuardianPolicy()
        p.onSample(0L, 40.5f, false)
        // brief dip below 37 does not restore
        val dip = p.onSample(10_000L, 36.9f, false)
        assertTrue(dip.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        // bounce back up resets the hold
        p.onSample(20_000L, 39f, false)
        p.onSample(30_000L, 36.5f, false)
        val stillWaiting = p.onSample(50_000L, 36.5f, false)
        assertTrue(stillWaiting.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        val recovered = p.onSample(91_000L, 36.5f, false)
        assertTrue(recovered.any { it is ThermalGuardianPolicy.Directive.RestoreControls })
        assertFalse(p.controlsApplied)
    }

    @Test
    fun `missing sensor never guesses`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, null, null)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.LogEvent })
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.ApplyLightControls })
        assertEquals(ThermalGuardianPolicy.Zone.COOL, p.zone)
    }

    @Test
    fun `test run reports truthfully and never applies controls`() {
        val p = ThermalGuardianPolicy()
        val hot = p.runTest(0L, 41f, true)
        assertTrue(hot.any { it is ThermalGuardianPolicy.Directive.Speak && it.test })
        assertTrue(hot.none { it is ThermalGuardianPolicy.Directive.ApplyActiveControls })
        assertFalse(p.controlsApplied)
        val noSensor = p.runTest(0L, null, null)
        assertTrue(noSensor.any { it is ThermalGuardianPolicy.Directive.Speak && it.test })
    }
}
