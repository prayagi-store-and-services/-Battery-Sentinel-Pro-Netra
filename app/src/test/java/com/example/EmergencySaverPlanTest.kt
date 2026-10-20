package com.example

import com.example.service.EmergencySaverPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySaverPlanTest {

    @Test
    fun `budget keeps the reserve and divides the rest`() {
        val b = EmergencySaverPlan.budget(30, 6)
        assertEquals(10, b.reservePercent)
        assertEquals(20, b.usablePercent)
        assertEquals(20f / 6f, b.targetPerHour, 0.001f)
    }

    @Test
    fun `budget never goes below zero`() {
        val b = EmergencySaverPlan.budget(8, 6)
        assertEquals(0, b.usablePercent)
        assertEquals(0f, b.targetPerHour, 0.001f)
    }

    @Test
    fun `airplane mode only when no network needed`() {
        val noNet = EmergencySaverPlan.plan(EmergencySaverPlan.Brand.REALME, EmergencySaverPlan.NetworkNeed.NONE, EmergencySaverPlan.Usage.ESSENTIAL, canWriteSettings = true)
        assertTrue(noNet.any { it.title.contains("Airplane") })
        val data = EmergencySaverPlan.plan(EmergencySaverPlan.Brand.REALME, EmergencySaverPlan.NetworkNeed.MOBILE_DATA, EmergencySaverPlan.Usage.NORMAL, canWriteSettings = true)
        assertTrue(data.none { it.title.contains("Airplane") })
    }

    @Test
    fun `brightness step is in-app only with write permission`() {
        val granted = EmergencySaverPlan.plan(EmergencySaverPlan.Brand.SAMSUNG, EmergencySaverPlan.NetworkNeed.CALLS_SMS, EmergencySaverPlan.Usage.NORMAL, canWriteSettings = true)
        assertTrue(granted.any { it.action == EmergencySaverPlan.StepAction.APPLY_IN_APP })
        val denied = EmergencySaverPlan.plan(EmergencySaverPlan.Brand.SAMSUNG, EmergencySaverPlan.NetworkNeed.CALLS_SMS, EmergencySaverPlan.Usage.NORMAL, canWriteSettings = false)
        assertTrue(denied.none { it.action == EmergencySaverPlan.StepAction.APPLY_IN_APP })
    }

    @Test
    fun `every settings step carries an intent action`() {
        val plan = EmergencySaverPlan.plan(EmergencySaverPlan.Brand.VIVO, EmergencySaverPlan.NetworkNeed.MOBILE_DATA, EmergencySaverPlan.Usage.NORMAL, canWriteSettings = false)
        plan.filter { it.action == EmergencySaverPlan.StepAction.OPEN_SETTINGS }
            .forEach { assertTrue(it.settingsAction != null) }
    }

    @Test
    fun `guided steps never claim the app applied them`() {
        val plan = EmergencySaverPlan.plan(EmergencySaverPlan.Brand.OTHER, EmergencySaverPlan.NetworkNeed.NONE, EmergencySaverPlan.Usage.ESSENTIAL, canWriteSettings = false)
        assertTrue(plan.none { it.action == EmergencySaverPlan.StepAction.APPLY_IN_APP })
        assertTrue(plan.any { it.detail.contains("Settings") })
    }

    @Test
    fun `brand paths are device specific`() {
        assertTrue(EmergencySaverPlan.batterySaverPath(EmergencySaverPlan.Brand.SAMSUNG).contains("device care"))
        assertTrue(EmergencySaverPlan.refreshRatePath(EmergencySaverPlan.Brand.REDMI_XIAOMI).contains("60Hz"))
        assertFalse(EmergencySaverPlan.batterySaverPath(EmergencySaverPlan.Brand.OTHER).isBlank())
    }
}
