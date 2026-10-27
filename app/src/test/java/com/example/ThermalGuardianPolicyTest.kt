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

    @Test
    fun `manufacturer thermal CRITICAL raises severity even when battery reads cool`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, 30f, false, thermalStatus = 4) // THERMAL_STATUS_CRITICAL
        assertEquals(ThermalGuardianPolicy.Zone.SEVERE, p.zone)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.ApplySevereControls })
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.Speak })
    }

    @Test
    fun `manufacturer thermal SEVERE raises to at least active`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, 36f, false, thermalStatus = 3) // THERMAL_STATUS_SEVERE
        assertEquals(ThermalGuardianPolicy.Zone.ACTIVE, p.zone)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.ApplyActiveControls })
    }

    @Test
    fun `manufacturer thermal CRITICAL with missing battery sensor still responds`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, null, null, thermalStatus = 5) // THERMAL_STATUS_EMERGENCY
        assertEquals(ThermalGuardianPolicy.Zone.SEVERE, p.zone)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.ApplySevereControls })
        // no invented temperature in the spoken line
        val speak = d.filterIsInstance<ThermalGuardianPolicy.Directive.Speak>().first()
        assertFalse(speak.text.contains("degrees"))
    }

    @Test
    fun `low manufacturer status never lowers a hot battery reading`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, 41f, false, thermalStatus = 1) // THERMAL_STATUS_LIGHT
        assertEquals(ThermalGuardianPolicy.Zone.ACTIVE, p.zone)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.ApplyActiveControls })
    }

    @Test
    fun `missing sensor and no thermal status stays quiet`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, null, null, thermalStatus = 0)
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.ApplyLightControls })
        assertEquals(ThermalGuardianPolicy.Zone.COOL, p.zone)
    }

    @Test
    fun `persistent critical manufacturer status blocks recovery despite cool battery`() {
        val p = ThermalGuardianPolicy()
        p.onSample(0L, 36f, false, thermalStatus = 4) // CRITICAL with a cool battery
        assertEquals(ThermalGuardianPolicy.Zone.SEVERE, p.zone)
        val d = p.onSample(60_000L, 36f, false, thermalStatus = 4)
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.ClearNotification })
        assertTrue(d.filterIsInstance<ThermalGuardianPolicy.Directive.Speak>().none { it.text.contains("cooled") })
        assertTrue(p.controlsApplied)
        assertEquals(ThermalGuardianPolicy.Zone.SEVERE, p.zone)
        // still blocked a long time later
        val d2 = p.onSample(300_000L, 36f, false, thermalStatus = 4)
        assertTrue(d2.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        assertTrue(p.controlsApplied)
    }

    @Test
    fun `persistent severe status 3 also blocks recovery`() {
        val p = ThermalGuardianPolicy()
        p.onSample(0L, 36f, false, thermalStatus = 3)
        val d = p.onSample(90_000L, 36f, false, thermalStatus = 3)
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        assertTrue(p.controlsApplied)
    }

    @Test
    fun `recovery happens after manufacturer status clears and cool hold is sustained`() {
        val p = ThermalGuardianPolicy()
        p.onSample(0L, 36f, false, thermalStatus = 4)
        p.onSample(60_000L, 36f, false, thermalStatus = 4)
        // status clears: the 60 s hold starts now, not from the earlier blocked readings
        val d1 = p.onSample(70_000L, 36f, false, thermalStatus = 0)
        assertTrue(d1.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        val d2 = p.onSample(129_000L, 36f, false, thermalStatus = 0)
        assertTrue(d2.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        val d3 = p.onSample(130_000L, 36f, false, thermalStatus = 0)
        assertTrue(d3.any { it is ThermalGuardianPolicy.Directive.RestoreControls })
        assertTrue(d3.any { it is ThermalGuardianPolicy.Directive.ClearNotification })
        assertFalse(p.controlsApplied)
    }

    @Test
    fun `status flapping back to critical restarts the cool hold`() {
        val p = ThermalGuardianPolicy()
        p.onSample(0L, 41f, false)
        p.onSample(10_000L, 36f, false, thermalStatus = 0)
        p.onSample(50_000L, 36f, false, thermalStatus = 4) // blocked, timer reset
        val d = p.onSample(75_000L, 36f, false, thermalStatus = 0) // timer restarts here
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        val d2 = p.onSample(134_000L, 36f, false, thermalStatus = 0)
        assertTrue(d2.none { it is ThermalGuardianPolicy.Directive.RestoreControls })
    }

    @Test
    fun `no manufacturer status still recovers on cool battery alone`() {
        val p = ThermalGuardianPolicy()
        p.onSample(0L, 41f, false)
        p.onSample(10_000L, 36f, false)
        val d = p.onSample(70_000L, 36f, false)
        assertTrue(d.any { it is ThermalGuardianPolicy.Directive.RestoreControls })
        assertFalse(p.controlsApplied)
    }

    @Test
    fun `spec band edges 39_4 light, 39_5 active, 44_9 severe, 45 critical`() {
        val p = ThermalGuardianPolicy()
        assertEquals(ThermalGuardianPolicy.Zone.MONITOR, p.zoneFor(37.9f))
        assertEquals(ThermalGuardianPolicy.Zone.LIGHT, p.zoneFor(38f))
        assertEquals(ThermalGuardianPolicy.Zone.LIGHT, p.zoneFor(39.4f))
        assertEquals(ThermalGuardianPolicy.Zone.ACTIVE, p.zoneFor(39.5f))
        assertEquals(ThermalGuardianPolicy.Zone.ACTIVE, p.zoneFor(41.9f))
        assertEquals(ThermalGuardianPolicy.Zone.SEVERE, p.zoneFor(42f))
        assertEquals(ThermalGuardianPolicy.Zone.SEVERE, p.zoneFor(44.9f))
        assertEquals(ThermalGuardianPolicy.Zone.CRITICAL, p.zoneFor(45f))
    }

    @Test
    fun `critical zone warns once, never claims cooling, and recovers only when cool`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, 45.5f, true)
        assertEquals(ThermalGuardianPolicy.Zone.CRITICAL, p.zone)
        val n = d.filterIsInstance<ThermalGuardianPolicy.Directive.Notify>().single()
        assertTrue(n.severe)
        assertTrue(n.body.contains("stop charging", ignoreCase = true))
        val say = d.filterIsInstance<ThermalGuardianPolicy.Directive.Speak>().single()
        assertTrue(say.critical)
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.ApplyLightControls || it is ThermalGuardianPolicy.Directive.ApplyActiveControls || it is ThermalGuardianPolicy.Directive.ApplySevereControls })
        val d2 = p.onSample(5_000L, 45.6f, true)
        assertTrue(d2.none { it is ThermalGuardianPolicy.Directive.Speak })
        assertTrue(p.onSample(70_000L, 44f, true).none { it is ThermalGuardianPolicy.Directive.RestoreControls })
        p.onSample(100_000L, 36f, false)
        val d3 = p.onSample(170_000L, 36f, false)
        assertTrue(d3.any { it is ThermalGuardianPolicy.Directive.RestoreControls })
    }

    @Test
    fun `critical warning does not repeat within 60 s when the phone hovers at 45`() {
        val p = ThermalGuardianPolicy()
        assertTrue(p.onSample(0L, 45.2f, false).any { it is ThermalGuardianPolicy.Directive.Notify })
        p.onSample(5_000L, 44.8f, false)
        val again = p.onSample(10_000L, 45.1f, false)
        assertTrue(again.none { it is ThermalGuardianPolicy.Directive.Notify || it is ThermalGuardianPolicy.Directive.Speak })
    }

    @Test
    fun `no sensor never reports a hot zone or notification`() {
        val p = ThermalGuardianPolicy()
        val d = p.onSample(0L, null, null)
        assertEquals(ThermalGuardianPolicy.Zone.COOL, p.zone)
        assertTrue(d.none { it is ThermalGuardianPolicy.Directive.Notify })
    }
}
