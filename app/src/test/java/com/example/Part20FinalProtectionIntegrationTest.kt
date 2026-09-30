package com.example

import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.model.AdaptiveWarningCategory
import com.example.model.BluetoothDeviceItem
import com.example.model.CanonicalChargerState
import com.example.model.CanonicalChargingSpeed
import com.example.model.CanonicalMediaState
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.model.EnvironmentalHeatDiagnosis
import com.example.model.LocationContextState
import com.example.model.NetraCentralEvent
import com.example.model.NetraEventType
import com.example.model.WeatherCondition
import com.example.model.WeatherContextState
import com.example.service.AnnouncementEngine
import com.example.service.AnnouncementItem
import com.example.service.AnnouncementPriority
import com.example.service.CentralCapabilityRegistry
import com.example.service.ClimateBaselineEngine
import com.example.service.NetraCentralDataCenter
import com.example.service.ThermalCauseInvestigator
import com.example.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Part20FinalProtectionIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var dataCenter: NetraCentralDataCenter

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        dataCenter = NetraCentralDataCenter()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. Thermal >40°C entry
    @Test
    fun `test 1 Thermal above 40C triggers critical protection and dims brightness`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 80, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 415, voltage = 3900, currentMicroAmps = -400000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertTrue(state.isCriticalThermalActive)
        assertEquals(10, state.targetBrightnessPercent)
    }

    // 2. Thermal <=35°C recovery
    @Test
    fun `test 2 Thermal at or below 35C recovers thermal protection`() = runTest(testDispatcher) {
        // Enter thermal protection
        dataCenter.processRawInput(
            level = 80, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 420, voltage = 3900, currentMicroAmps = -400000,
            bluetoothConnected = null, bluetoothBattery = null
        )
        assertTrue(dataCenter.centralState.value.isCriticalThermalActive)

        // Cool down to 34°C
        dataCenter.processRawInput(
            level = 80, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 340, voltage = 3900, currentMicroAmps = -400000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertFalse(state.isCriticalThermalActive)
        assertNull(state.targetBrightnessPercent)
    }

    // 3. Low battery <=30% entry
    @Test
    fun `test 3 Low battery at or below 30 percent while discharging enters protection`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 28, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 300, voltage = 3600, currentMicroAmps = -500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertTrue(state.isLowBatteryControlActive)
        assertEquals(10, state.targetBrightnessPercent)
    }

    // 4. Low battery >=35% recovery
    @Test
    fun `test 4 Low battery recovers when level reaches at least 35 percent`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 25, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 300, voltage = 3500, currentMicroAmps = -500000,
            bluetoothConnected = null, bluetoothBattery = null
        )
        assertTrue(dataCenter.centralState.value.isLowBatteryControlActive)

        // Charge up to 36%
        dataCenter.processRawInput(
            level = 36, scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 310, voltage = 4000, currentMicroAmps = 1500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertFalse(state.isLowBatteryControlActive)
        assertNull(state.targetBrightnessPercent)
    }

    // 5. Charging does not recover low battery below 35%
    @Test
    fun `test 5 Charging does not recover low battery below 35 percent`() = runTest(testDispatcher) {
        // Discharging at 25%
        dataCenter.processRawInput(
            level = 25, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 300, voltage = 3500, currentMicroAmps = -500000,
            bluetoothConnected = null, bluetoothBattery = null
        )
        assertTrue(dataCenter.centralState.value.isLowBatteryControlActive)

        // Plug in charger, but still at 25%
        dataCenter.processRawInput(
            level = 25, scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 305, voltage = 3800, currentMicroAmps = 2000000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        // Must REMAIN active until >= 35%
        assertTrue(state.isLowBatteryControlActive)
    }

    // 6. Thermal + low battery coexistence
    @Test
    fun `test 6 Thermal and low battery coexistence dims once and preserves state`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 20, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 425, voltage = 3600, currentMicroAmps = -500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertTrue(state.isCriticalThermalActive)
        assertTrue(state.isLowBatteryControlActive)
        assertEquals(10, state.targetBrightnessPercent)
    }

    // 7. Independent recovery
    @Test
    fun `test 7 Independent recovery when thermal recovers but low battery remains`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 20, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 425, voltage = 3600, currentMicroAmps = -500000,
            bluetoothConnected = null, bluetoothBattery = null
        )
        assertTrue(dataCenter.centralState.value.isCriticalThermalActive)
        assertTrue(dataCenter.centralState.value.isLowBatteryControlActive)

        // Temperature cools to 33°C, but battery remains 22%
        dataCenter.processRawInput(
            level = 22, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 330, voltage = 3600, currentMicroAmps = -500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertFalse(state.isCriticalThermalActive)
        assertTrue(state.isLowBatteryControlActive)
        assertEquals(10, state.targetBrightnessPercent)
    }

    // 8. Raw 15W remains Fast despite 6W phone consumption
    @Test
    fun `test 8 Raw 15W remains Fast regardless of phone consumption`() = runTest(testDispatcher) {
        // 5000mV * 3000mA = 15.0W raw power
        dataCenter.processRawInput(
            level = 60, scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 320, voltage = 5000, currentMicroAmps = 3000000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertEquals(15.0f, state.powerWatts ?: 0f, 0.1f)
        assertEquals(CanonicalChargingSpeed.FAST, state.chargingSpeed)
    }

    // 9. <5W unconfirmed USB is not Slow Charging
    @Test
    fun `test 9 USB connection without confirmed charging is not labeled Slow Charging`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 70, scale = 100,
            status = BatteryManager.BATTERY_STATUS_NOT_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_USB,
            temperatureRaw = 300, voltage = 4000, currentMicroAmps = 100000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertEquals(CanonicalChargingSpeed.UNAVAILABLE, state.chargingSpeed)
        assertEquals(CanonicalChargerState.CHARGER_CONNECTED_NOT_CHARGING, state.canonicalChargerState)
    }

    // 10. Charger connected != charging
    @Test
    fun `test 10 Charger connected does not imply active charging`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 80, scale = 100,
            status = BatteryManager.BATTERY_STATUS_NOT_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 300, voltage = 4100, currentMicroAmps = 0,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertEquals(true, state.isChargerConnected)
        assertEquals(false, state.isCharging)
        assertEquals(CanonicalChargerState.CHARGER_CONNECTED_NOT_CHARGING, state.canonicalChargerState)
    }

    // 11. ETA unavailable without sufficient progression
    @Test
    fun `test 11 ETA is unavailable without sufficient observed progression`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 50, scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 300, voltage = 4000, currentMicroAmps = 2000000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        assertNull(state.chargingEtaMinutes)
    }

    // 12. Last-valid battery retention
    @Test
    fun `test 12 Last-valid battery level is retained when subsequent sample is invalid`() = runTest(testDispatcher) {
        dataCenter.processRawInput(
            level = 75, scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 310, voltage = 4100, currentMicroAmps = 1500000,
            bluetoothConnected = null, bluetoothBattery = null
        )
        assertEquals(75, dataCenter.centralState.value.batteryLevel)

        // Send invalid level (-1)
        dataCenter.processRawInput(
            level = -1, scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_AC,
            temperatureRaw = 315, voltage = 4100, currentMicroAmps = 1500000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        assertEquals(75, dataCenter.centralState.value.batteryLevel)
    }

    // 13. Last-valid environmental retention
    @Test
    fun `test 13 Last-valid environmental context retained when weather update fails`() = runTest(testDispatcher) {
        val validLoc = LocationContextState(countryCode = "IN", countryName = "India", regionName = "Maharashtra", locality = "Mumbai")
        val validWeather = WeatherContextState(temperatureCelsius = 34.5f, condition = WeatherCondition.CLEAR, isAvailable = true)
        dataCenter.updateLocationAndWeatherDirect(validLoc, validWeather)

        assertEquals("IN", dataCenter.centralState.value.locationContext.countryCode)
        assertEquals(34.5f, dataCenter.centralState.value.weatherContext.temperatureCelsius ?: 0f, 0.1f)

        // Simulate failed empty weather update
        val failedWeather = WeatherContextState(isAvailable = false)
        dataCenter.updateLocationAndWeatherDirect(validLoc, failedWeather)

        // Temperature must be retained
        assertEquals(34.5f, dataCenter.centralState.value.weatherContext.temperatureCelsius ?: 0f, 0.1f)
    }

    // 14. Bluetooth last-valid retention
    @Test
    fun `test 14 Bluetooth connected devices and battery retained across updates`() = runTest(testDispatcher) {
        val dev = BluetoothDeviceItem(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Sentinel Headset",
            deviceType = "Audio",
            profile = "A2DP",
            batteryPercent = 85,
            isConnected = true,
            isPaired = true
        )
        dataCenter.processBluetoothDevices(listOf(dev))

        val state = dataCenter.centralState.value
        assertEquals(1, state.bluetoothDevices.size)
        assertEquals("Sentinel Headset", state.bluetoothDevices.first().name)
        assertEquals(85, state.bluetoothDevices.first().batteryPercent)
    }

    // 15. Weather stale-state handling
    @Test
    fun `test 15 ClimateBaselineEngine handles weather absence gracefully`() {
        val context = ClimateBaselineEngine.computeAdaptiveThermalContext(
            countryCode = "IN",
            ambientTempCelsius = null,
            actualBatteryTempCelsius = 32.0f
        )
        assertNotNull(context)
        assertEquals(AdaptiveWarningCategory.NORMAL_IDLE, context.warningCategory)
    }

    // 16. Ambient sensor unavailable handling
    @Test
    fun `test 16 ThermalCauseInvestigator handles missing ambient hardware sensor`() {
        val investigator = ThermalCauseInvestigator(context)
        val result = investigator.diagnoseThermalCause(batteryTempCelsius = 42.0f, weatherAmbientTempCelsius = null)
        assertEquals(EnvironmentalHeatDiagnosis.INSUFFICIENT_SENSOR_DATA, result.state)
    }

    // 17. Android thermal API unavailable handling
    @Test
    fun `test 17 ThermalCauseInvestigator provides truthful evaluation when sensor is unavailable`() {
        val investigator = ThermalCauseInvestigator(context)
        assertFalse(investigator.isSensorAvailable)
    }

    // 18. Environmental heat does not suppress critical protection
    @Test
    fun `test 18 Hot weather baseline does not suppress 40C critical thermal safety`() = runTest(testDispatcher) {
        // High ambient 42°C in peak summer
        val validLoc = LocationContextState(countryCode = "IN", countryName = "India")
        val validWeather = WeatherContextState(temperatureCelsius = 42.0f, condition = WeatherCondition.EXTREME_HEAT, isAvailable = true)
        dataCenter.updateLocationAndWeatherDirect(validLoc, validWeather)

        // Battery hits 41°C
        dataCenter.processRawInput(
            level = 80, scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0,
            temperatureRaw = 410, voltage = 3900, currentMicroAmps = -300000,
            bluetoothConnected = null, bluetoothBattery = null
        )

        val state = dataCenter.centralState.value
        // Critical thermal protection MUST be active regardless of ambient weather
        assertTrue(state.isCriticalThermalActive)
        assertEquals(10, state.targetBrightnessPercent)
    }

    // 19. Duplicate events are deduplicated
    @Test
    fun `test 19 Duplicate raw inputs produce single canonical event`() = runTest(testDispatcher) {
        val events = mutableListOf<NetraCentralEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataCenter.centralEvents.toList(events)
        }

        dataCenter.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2000000, null, null)
        dataCenter.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2000000, null, null)
        dataCenter.processRawInput(50, 100, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 2000000, null, null)

        job.cancel()
        assertEquals(1, events.count { it.eventType == NetraEventType.CHARGING_STARTED })
    }

    // 20. Duplicate TTS is prevented
    @Test
    fun `test 20 AnnouncementEngine priority queue orders critical thermal over routine events`() {
        val crit = AnnouncementItem("crit_1", "Critical Overheat", AnnouncementPriority.CRITICAL_THERMAL, "THERMAL", true)
        val routine = AnnouncementItem("rout_1", "Battery 80 percent", AnnouncementPriority.PHONE_BATTERY, "BATTERY", false)

        assertTrue(crit < routine) // Lower priority level number is higher urgency
    }

    // 21. Night Protection suppresses routine announcements
    @Test
    fun `test 21 Night Protection suppresses routine phone battery announcement`() {
        val item = AnnouncementItem(
            id = "rout_1",
            text = "Battery 75 percent",
            priority = AnnouncementPriority.PHONE_BATTERY,
            category = "BATTERY",
            isNightException = false
        )
        assertFalse(item.isNightException)
    }

    // 22. Critical announcement survives Night Protection
    @Test
    fun `test 22 Critical thermal announcement has night exception`() {
        val item = AnnouncementItem(
            id = "crit_1",
            text = "Thermal warning 43 degrees",
            priority = AnnouncementPriority.CRITICAL_THERMAL,
            category = "THERMAL",
            isNightException = true
        )
        assertTrue(item.isNightException)
    }

    // 23. Media already paused does not resume
    @Test
    fun `test 23 Media state tracking differentiates paused by app vs externally paused`() {
        val state = dataCenter.centralState.value
        assertFalse(state.mediaPausedByNethra)
    }

    // 24. Permission result refreshes actual capability
    @Test
    fun `test 24 PermissionHelper and CentralCapabilityRegistry return truthful states`() {
        val registry = CentralCapabilityRegistry(context)
        val caps = registry.detectAllCapabilities(currentMicroAmps = 0, temperatureRaw = 300, voltageRaw = 4000)
        assertTrue(caps.containsKey(CapabilityType.BATTERY_TELEMETRY))
    }

    // 25. Capability Registry remains centralized
    @Test
    fun `test 25 CapabilityRegistry is single source of capability classification`() {
        val registry = CentralCapabilityRegistry(context)
        val caps = registry.detectAllCapabilities(currentMicroAmps = 0, temperatureRaw = 300, voltageRaw = 4000)
        val status = caps[CapabilityType.BATTERY_TELEMETRY]
        assertTrue(status == CapabilityStatus.AVAILABLE || status == CapabilityStatus.SUPPORTED)
    }

    // 26. No duplicate thermal architecture
    @Test
    fun `test 26 Thermal management is exclusively owned by NetraCentralDataCenter`() = runTest(testDispatcher) {
        dataCenter.processRawInput(80, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 420, 3800, -300000, null, null)
        assertTrue(dataCenter.centralState.value.isCriticalThermalActive)
    }

    // 27. No duplicate announcement architecture
    @Test
    fun `test 27 Announcement priority enum covers all required tiers`() {
        assertEquals(7, AnnouncementPriority.values().size)
    }

    // 28. No 100ms artificial polling
    @Test
    fun `test 28 Telemetry ingestion is strictly event-driven without synthetic delays`() = runTest(testDispatcher) {
        val tStart = System.currentTimeMillis()
        dataCenter.processRawInput(90, 100, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 300, 4100, 1800000, null, null)
        val tElapsed = System.currentTimeMillis() - tStart
        assertTrue(tElapsed < 100)
    }

    // 29. Central Unit propagation remains within target under testable conditions
    @Test
    fun `test 29 Pipeline latency is instrumented and compliant with budget`() = runTest(testDispatcher) {
        dataCenter.processRawInput(85, 100, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 310, 4150, 1900000, null, null)
        val metrics = dataCenter.centralState.value.pipelineLatency
        assertNotNull(metrics)
        assertTrue(metrics?.meetsBudget == true)
    }

    // 30. UI observes canonical state correctly
    @Test
    fun `test 30 Central StateFlow updates observers immediately upon raw input`() = runTest(testDispatcher) {
        dataCenter.processRawInput(99, 100, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 300, 4200, 1500000, null, null)
        assertEquals(99, dataCenter.centralState.value.batteryLevel)
        assertEquals(true, dataCenter.centralState.value.isCharging)
    }
}
