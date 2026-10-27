package com.example

import com.example.model.CanonicalMediaState
import com.example.model.ChargingOptimizationMode
import com.example.service.NetraCentralDataCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class CanonicalStatePublicationTest {
    @Test
    fun staleTelemetryCandidateCannotEraseNewerMediaOrControls() = runBlocking {
        val center = NetraCentralDataCenter()
        // Exactly the stale-snapshot ordering from the separate telemetry/media locks.
        val candidate = center.centralState.value.copy(batteryLevel = 88)
        center.updateMediaState(CanonicalMediaState.PAUSED)
        center.setMediaPausedByNethra(true)
        center.updateMediaControlAvailable(true)
        center.updateScreenState(false, true)
        center.updateChargingOptimizationMode(ChargingOptimizationMode.LIMITED_THERMAL)
        center.updateThermalStatus(4)
        center.publishTelemetryState(candidate)
        val state = center.centralState.value
        assertEquals(88, state.batteryLevel)
        assertEquals(CanonicalMediaState.PAUSED, state.mediaState)
        assertTrue(state.mediaPausedByNethra)
        assertTrue(state.isMediaControlAvailable)
        assertFalse(state.isScreenOn)
        assertTrue(state.isScreenOffConfirmed)
        assertEquals(ChargingOptimizationMode.LIMITED_THERMAL, state.chargingOptimizationMode)
        assertEquals(4, state.thermalStatus)
    }

    @Test
    fun simultaneousMediaAndTelemetryPublicationsKeepBothResults() = runBlocking {
        val center = NetraCentralDataCenter()
        repeat(250) { index ->
            center.updateMediaState(CanonicalMediaState.STOPPED)
            center.setMediaPausedByNethra(false)
            val candidate = center.centralState.value.copy(batteryLevel = index % 101)
            val barrier = java.util.concurrent.CyclicBarrier(2)
            val telemetry = async(Dispatchers.Default) {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS)
                center.publishTelemetryState(candidate)
            }
            val media = async(Dispatchers.Default) {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS)
                center.updateMediaState(CanonicalMediaState.PAUSED)
                center.setMediaPausedByNethra(true)
            }
            telemetry.await()
            media.await()
            val state = center.centralState.value
            assertEquals(index % 101, state.batteryLevel)
            assertEquals(CanonicalMediaState.PAUSED, state.mediaState)
            assertTrue(state.mediaPausedByNethra)
        }
    }
    @Test
    fun staleTelemetryRetainsLatestBluetoothClimateAndCapabilities() = runBlocking {
        val center = NetraCentralDataCenter()
        center.initCapabilityRegistry(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        center.updateLocationAndWeatherDirect(
            com.example.model.LocationContextState(countryCode = "IN"),
            com.example.model.WeatherContextState(temperatureCelsius = 28f, isAvailable = true)
        )
        val candidate = center.centralState.value.copy(batteryLevel = 88, temperatureCelsius = 39f,
            capabilities = mapOf(com.example.model.CapabilityType.BATTERY_CURRENT to com.example.model.CapabilityStatus.AVAILABLE))
        center.processBluetoothDevices(listOf(com.example.model.BluetoothDeviceItem(
            name = "Headset", address = "AA:BB", isConnected = true, isPaired = true,
            deviceType = "Headset", batteryPercent = 65, profile = "A2DP")))
        center.updateLocationAndWeatherDirect(
            com.example.model.LocationContextState(countryCode = "GB"),
            com.example.model.WeatherContextState(temperatureCelsius = 15f, isAvailable = true))
        val before = center.centralState.value
        center.publishTelemetryState(candidate)
        val state = center.centralState.value
        assertEquals(true, state.bluetoothConnected)
        assertEquals(65, state.bluetoothBatteryPercent)
        assertEquals(before.bluetoothDevices, state.bluetoothDevices)
        assertEquals("GB", state.locationContext.countryCode)
        assertEquals(15f, state.weatherContext.temperatureCelsius)
        assertEquals(com.example.service.ClimateBaselineEngine.computeAdaptiveThermalContext(
            countryCode = "GB", ambientTempCelsius = 15f, actualBatteryTempCelsius = 39f,
            deviceIdleState = candidate.deviceIdleState, isCharging = candidate.isCharging), state.adaptiveThermalContext)
        assertEquals(com.example.model.CapabilityStatus.AVAILABLE,
            state.capabilities[com.example.model.CapabilityType.BATTERY_CURRENT])
        val unrelatedCapabilities = before.capabilities.filterKeys { it == com.example.model.CapabilityType.BLUETOOTH_CONNECTED_INFO }
        assertTrue(unrelatedCapabilities.isNotEmpty())
        unrelatedCapabilities.forEach { (key, value) ->
            assertEquals(value, state.capabilities[key])
        }
    }

    @Test
    fun realRawTelemetryEntryPointAndMediaKeepBothResults() = runBlocking {
        val center = NetraCentralDataCenter()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        center.initCapabilityRegistry(context)
        repeat(40) { index ->
            center.updateMediaState(CanonicalMediaState.STOPPED)
            center.setMediaPausedByNethra(false)
            val barrier = java.util.concurrent.CyclicBarrier(2)
            val telemetry = async(Dispatchers.Default) {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS)
                center.processRawInput(index, 100, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING,
                    0, 300, 3900, -500000, null, null)
            }
            val media = async(Dispatchers.Default) {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS)
                center.updateMediaState(CanonicalMediaState.PAUSED)
                center.setMediaPausedByNethra(true)
            }
            telemetry.await()
            media.await()
            val state = center.centralState.value
            assertEquals(index, state.batteryLevel)
            assertEquals(CanonicalMediaState.PAUSED, state.mediaState)
            assertTrue(state.mediaPausedByNethra)
        }
    }

}
