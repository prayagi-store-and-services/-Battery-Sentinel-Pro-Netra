package com.example

import com.example.service.SaverDecision
import com.example.service.SaverPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverPolicyTest {
    private fun d(enabled: Boolean = true, active: Boolean = false, t: Float? = 25f, l: Int = 80, ch: Boolean = false, tl: Float = 30f, ll: Int = 35) =
        SaverPolicy.decide(enabled, active, t, l, ch, tl, ll)

    @Test fun startsWhenHot() = assertEquals(SaverDecision.APPLY, d(t = 30f))
    @Test fun startsWhenLowAndNotCharging() = assertEquals(SaverDecision.APPLY, d(l = 35))
    @Test fun lowButChargingDoesNotStart() = assertEquals(SaverDecision.NONE, d(l = 20, ch = true))
    @Test fun normalDoesNotStart() = assertEquals(SaverDecision.NONE, d())
    @Test fun unknownTemperatureNeverTriggersHeat() = assertEquals(SaverDecision.NONE, d(t = null))
    @Test fun staysActiveInsideMargin() {
        assertEquals(SaverDecision.NONE, d(active = true, t = 29f))
        assertEquals(SaverDecision.NONE, d(active = true, l = 36))
    }
    @Test fun restoresWhenBackToNormal() = assertEquals(SaverDecision.RESTORE, d(active = true, t = 27f, l = 40))
    @Test fun restoresWhenChargingAndCool() = assertEquals(SaverDecision.RESTORE, d(active = true, t = 25f, l = 20, ch = true))
    @Test fun turningOffWhileActiveRestores() = assertEquals(SaverDecision.RESTORE, d(enabled = false, active = true))
    @Test fun disabledAndIdleDoesNothing() = assertEquals(SaverDecision.NONE, d(enabled = false, t = 40f))
    @Test fun limitsAreClamped() {
        assertEquals(25f, SaverPolicy.clampTemp(10f), 0f)
        assertEquals(45f, SaverPolicy.clampTemp(60f), 0f)
        assertEquals(5, SaverPolicy.clampLevel(0))
        assertEquals(60, SaverPolicy.clampLevel(90))
    }
    @Test fun netraAppsAreProtected() {
        assertTrue(SaverPolicy.isNetraPackage("com.aistudio.kbc.x"))
        assertTrue(SaverPolicy.isNetraPackage("com.prayagi.netraeco"))
        assertFalse(SaverPolicy.isNetraPackage("com.whatsapp"))
    }
}
