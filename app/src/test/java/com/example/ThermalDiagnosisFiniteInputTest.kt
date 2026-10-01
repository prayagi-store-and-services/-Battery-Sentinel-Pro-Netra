package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.model.EnvironmentalHeatDiagnosis
import com.example.service.ThermalCauseInvestigator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThermalDiagnosisFiniteInputTest {
    private fun investigator() = ThermalCauseInvestigator(ApplicationProvider.getApplicationContext<Application>())

    @Test fun nanAmbientCannotReportNormalContext() {
        assertEquals(EnvironmentalHeatDiagnosis.INSUFFICIENT_SENSOR_DATA,
            investigator().diagnoseThermalCause(42f, Float.NaN).state)
    }
    @Test fun infiniteAmbientCannotAttributeEnvironmentalHeat() {
        for (ambient in listOf(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(EnvironmentalHeatDiagnosis.INSUFFICIENT_SENSOR_DATA,
                investigator().diagnoseThermalCause(42f, ambient, isCharging = true).state)
        }
    }
    @Test fun nanBatteryCannotReportNormalContext() {
        assertEquals(EnvironmentalHeatDiagnosis.INSUFFICIENT_SENSOR_DATA,
            investigator().diagnoseThermalCause(Float.NaN, 25f).state)
    }
    @Test fun infiniteBatteryCannotAttributeInternalHeat() {
        for (battery in listOf(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(EnvironmentalHeatDiagnosis.INSUFFICIENT_SENSOR_DATA,
                investigator().diagnoseThermalCause(battery, 25f).state)
        }
    }
    @Test fun finiteInputsKeepExistingDiagnosis() {
        assertEquals(EnvironmentalHeatDiagnosis.INTERNAL_HEAT_LIKELY,
            investigator().diagnoseThermalCause(42f, 25f).state)
        assertEquals(EnvironmentalHeatDiagnosis.ENVIRONMENTAL_HEAT_LIKELY,
            investigator().diagnoseThermalCause(42f, 37f).state)
        assertEquals(EnvironmentalHeatDiagnosis.MIXED_HEAT_CONTEXT,
            investigator().diagnoseThermalCause(42f, 37f, isCharging = true).state)
    }
}
