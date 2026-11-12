package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.service.AlertDeliveryState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertDeliveryStateTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = context.getSharedPreferences("netra_alert_delivery_v1", Context.MODE_PRIVATE)
    @Before fun reset() { preferences.edit().clear().commit() }

    @Test fun mismatchedUnknownFutureAndCorruptRecordsCannotSuppressAlerts() {
        val state = AlertDeliveryState(context)
        assertTrue(state.saveTarget(100L, 80, 200L, true, 100))
        assertNull(state.loadTarget(null, 300L))
        assertNull(state.loadTarget(101L, 300L))
        assertNull(state.loadTarget(100L, 150L))
        assertNull(state.loadTarget(100L, 50L))
        preferences.edit().putString("session", "corrupt").commit()
        assertNull(state.loadTarget(100L, 300L))
    }

    @Test fun targetCannotPredateSessionOrUseInvalidTarget() {
        val state = AlertDeliveryState(context)
        assertFalse(state.saveTarget(200L, 80, 100L, false, null))
        assertFalse(state.saveTarget(100L, 101, 200L, false, null))
        assertFalse(state.saveTarget(null, 80, 200L, false, null))
        preferences.edit().putLong("session", 100L).putLong("target_at", 50L).putInt("target",80).commit()
        assertNull(state.loadTarget(100L, 300L))
    }

    @Test fun wrongSessionActionCannotUpdateRecord() {
        val state = AlertDeliveryState(context)
        state.saveTarget(100L, 80, 200L, false, null)
        state.action(101L, mute = true, override = 100, now = 300L)
        assertEquals(AlertDeliveryState.Target(100L, 80, 200L, false, null), state.loadTarget(100L, 300L))
    }

    @Test fun thresholdStorageIsBoundedAndChangingThresholdRearms() {
        val state = AlertDeliveryState(context)
        for (threshold in 30..80) state.recordThreshold(threshold.toFloat(), 200L)
        assertEquals(2, preferences.all.size)
        assertEquals(0L, state.thresholdAt(79f, 300L))
        assertEquals(200L, state.thresholdAt(80f, 300L))
        assertEquals(0L, state.thresholdAt(80f, 100L))
    }

    @Test fun inaccessiblePreferencesFailOpenWithoutThrowing() {
        val c = mock(Context::class.java)
        `when`(c.applicationContext).thenReturn(c)
        `when`(c.getSharedPreferences(anyString(), anyInt())).thenThrow(IllegalStateException("unavailable"))
        val state = AlertDeliveryState(c)
        assertNull(state.loadTarget(100L, 300L))
        assertFalse(state.saveTarget(100L, 80, 200L, false, null))
        state.clearTarget(); state.recordCooldown("overheat_at", 200L); state.recordThreshold(40f,200L)
        state.action(100L, mute = true, now = 300L)
        assertEquals(0L, state.cooldownAt("overheat_at",300L))
        assertEquals(0L, state.thresholdAt(40f,300L))
    }

    @Test fun writeFailureDoesNotPretendToPersistTarget() {
        val c = mock(Context::class.java)
        val p = mock(SharedPreferences::class.java)
        `when`(c.applicationContext).thenReturn(c)
        `when`(c.getSharedPreferences(anyString(), anyInt())).thenReturn(p)
        `when`(p.edit()).thenThrow(IllegalStateException("read only"))
        val state = AlertDeliveryState(c)
        assertFalse(state.saveTarget(100L, 80, 200L, false, null))
        state.clearTarget();state.recordThreshold(40f,200L);state.recordCooldown("overheat_at",200L)
        assertNull(state.loadTarget(100L,300L))
    }
    @Test fun conditionAttemptSurvivesRecreationAndFreshRecoveryRearms() {
        val first=AlertDeliveryState(context)
        assertTrue(first.claimCondition("LOW_BATTERY_PROTECTION",100L))
        val second=AlertDeliveryState(context)
        assertFalse(second.claimCondition("LOW_BATTERY_PROTECTION",200L))
        second.observeRecovery(34,30f,true)
        assertFalse(second.claimCondition("LOW_BATTERY_PROTECTION",300L))
        second.observeRecovery(35,30f,true)
        assertTrue(AlertDeliveryState(context).claimCondition("LOW_BATTERY_PROTECTION",400L))
    }

    @Test fun unknownOrStaleRecoveryCannotRearmCondition() {
        val first=AlertDeliveryState(context)
        assertTrue(first.claimCondition("LOW_BATTERY_PROTECTION",100L))
        first.observeRecovery(100,20f,false)
        first.observeRecovery(null,null,true)
        assertFalse(AlertDeliveryState(context).claimCondition("LOW_BATTERY_PROTECTION",200L))
    }

    @Test fun thermalEscalationIsSeparateAndRecoveryKeepsExistingHysteresis() {
        val state=AlertDeliveryState(context)
        assertTrue(state.claimCondition("THERMAL_WARNING",100L))
        assertTrue(AlertDeliveryState(context).claimCondition("THERMAL_CRITICAL",200L))
        assertTrue(state.claimCondition("THERMAL_PROTECTION",200L))
        state.observeRecovery(null,39f,true)
        assertTrue(AlertDeliveryState(context).claimCondition("THERMAL_WARNING",300L))
        assertTrue(AlertDeliveryState(context).claimCondition("THERMAL_CRITICAL",300L))
        assertFalse(AlertDeliveryState(context).claimCondition("THERMAL_PROTECTION",300L))
        state.observeRecovery(null,35f,true)
        assertTrue(AlertDeliveryState(context).claimCondition("THERMAL_PROTECTION",400L))
    }

    @Test fun futureConditionTimestampFailsOpenAndUnknownCategoryIsNotPersisted() {
        preferences.edit().putLong("condition_THERMAL_CRITICAL",900L).commit()
        val state=AlertDeliveryState(context)
        assertTrue(state.claimCondition("THERMAL_CRITICAL",100L))
        assertTrue(state.claimCondition("CONTINUOUS_CHARGE_ALERT",200L))
        assertTrue(state.claimCondition("CONTINUOUS_CHARGE_ALERT",200L))
        assertFalse(preferences.contains("condition_CONTINUOUS_CHARGE_ALERT"))
    }

    @Test fun retainedDisplayValueCannotPretendRecovery() {
        val state=AlertDeliveryState(context)
        state.claimCondition("THERMAL_CRITICAL",100L)
        val sample=com.example.model.NetraCentralState(temperatureCelsius=30f,isDataFresh=true,lastUpdateTimestamp=200L,
            fieldStates=com.example.model.TelemetryFieldState(tempStatus=com.example.model.FieldStatus.LAST_VALID,tempObservedAt=100L))
        state.observeRecovery(sample)
        assertFalse(AlertDeliveryState(context).claimCondition("THERMAL_CRITICAL",300L))
        state.observeRecovery(sample.copy(fieldStates=sample.fieldStates.copy(tempStatus=com.example.model.FieldStatus.LIVE,tempObservedAt=200L)))
        assertTrue(AlertDeliveryState(context).claimCondition("THERMAL_CRITICAL",400L))
    }

}
