package com.example

import com.example.model.BatteryTelemetry
import com.example.service.toTrackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard for plan item 3: BatteryForegroundService state now derives from
 * BatteryMonitorService.liveTelemetryFlow. The mapping must be lossless for every field the
 * tracking state exposes.
 */
class TelemetryTrackStateMappingTest {

    private val telemetry = BatteryTelemetry(
        level = 67,
        isCharging = true,
        pluggedType = "USB",
        temperature = 33.4f,
        voltageMv = 4123,
        currentMa = 850
    )

    @Test fun `every tracked field carries over`() {
        val state = telemetry.toTrackState(isTrackingActive = true)
        assertEquals(67, state.percentage)
        assertEquals(33.4f, state.temperatureCelsius, 0.001f)
        assertTrue(state.isCharging)
        assertEquals(4123, state.voltageMv)
        assertEquals(850_000, state.currentMicroAmps) // mA -> microamps
        assertTrue(state.isTrackingActive)
        assertTrue(state.timestamp > 0L)
    }

    @Test fun `plugged source labels match the previous receiver parsing`() {
        assertEquals("AC Charger", telemetry.copy(pluggedType = "AC").toTrackState(true).pluggedSource)
        assertEquals("USB Port", telemetry.copy(pluggedType = "USB").toTrackState(true).pluggedSource)
        assertEquals("Wireless", telemetry.copy(pluggedType = "WIRELESS").toTrackState(true).pluggedSource)
        assertEquals("Connected", telemetry.copy(pluggedType = "UNKNOWN").toTrackState(true).pluggedSource)
        assertEquals("Unplugged", telemetry.copy(pluggedType = "BATTERY", isCharging = false).toTrackState(true).pluggedSource)
    }

    @Test fun `status label follows charging state`() {
        assertEquals("Charging", telemetry.toTrackState(true).status)
        assertEquals("Discharging", telemetry.copy(isCharging = false).toTrackState(true).status)
    }

    @Test fun `percentage stays inside 0 to 100`() {
        assertEquals(100, telemetry.copy(level = 140).toTrackState(true).percentage)
        assertEquals(0, telemetry.copy(level = -5).toTrackState(true).percentage)
    }

    @Test fun `tracking flag is caller-controlled`() {
        assertFalse(telemetry.toTrackState(isTrackingActive = false).isTrackingActive)
    }
}
