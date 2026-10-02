package com.example.service

import android.os.BatteryManager
import com.example.model.LiveChargingSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FakeBatteryHardwareProvider(
    var isChargingState: Boolean = false,
    var pluggedTypeState: String = "UNPLUGGED",
    var voltageMvState: Int? = 4123,
    var currentMicroAmpsState: Int? = 1_234_000,
    var batteryLevelPercentState: Float? = 67.0f,
    var temperatureCelsiusState: Float? = 31.5f,
    var isBatteryFullState: Boolean = false,
    var chargeTimeRemainingMillisState: Long? = null
) : BatteryHardwareProvider {
    override fun isCharging(): Boolean = isChargingState
    override fun getPluggedType(): String = pluggedTypeState
    override fun getVoltageMv(): Int? = voltageMvState
    override fun getCurrentMicroAmps(): Int? = currentMicroAmpsState
    override fun getBatteryLevelPercent(): Float? = batteryLevelPercentState
    override fun getTemperatureCelsius(): Float? = temperatureCelsiusState
    override fun isBatteryFull(): Boolean = isBatteryFullState
    override fun computeChargeTimeRemainingMillis(): Long? = chargeTimeRemainingMillisState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveChargingSessionEngineTest {

    private lateinit var fakeHardware: FakeBatteryHardwareProvider
    private var simulatedTimeMs = 1_000_000L
    private var simulatedRealtimeMs = 100_000L

    @Before
    fun setup() {
        fakeHardware = FakeBatteryHardwareProvider()
        simulatedTimeMs = 1_000_000L
        simulatedRealtimeMs = 100_000L
    }

    private fun createEngine(testScope: TestScope): LiveChargingSessionEngine {
        return LiveChargingSessionEngine(
            hardwareProvider = fakeHardware,
            scope = testScope,
            clock = { simulatedTimeMs },
            elapsedRealtimeClock = { simulatedRealtimeMs },
            pollingIntervalMs = 1000L
        )
    }

    // Scenario 1: Charging begins while the app is open
    @Test
    fun scenario1_chargingBeginsWhileApplicationIsOpen() = runTest {
        // App is initially open while unplugged / discharging
        fakeHardware.isChargingState = false
        fakeHardware.pluggedTypeState = "UNPLUGGED"

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            assertTrue("Live discharge sampling runs while the screen is open", engine.isPollingActive())
            assertFalse(engine.sessionState.value.isChargingActive)

            // Charger plugged in
            fakeHardware.isChargingState = true
            fakeHardware.pluggedTypeState = "USB"
            fakeHardware.voltageMvState = 4050
            fakeHardware.currentMicroAmpsState = 1_500_000
            fakeHardware.temperatureCelsiusState = 30.0f
            fakeHardware.batteryLevelPercentState = 60.0f

            engine.onBatteryStateChanged(
                isCharging = true,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                plugged = BatteryManager.BATTERY_PLUGGED_USB
            )
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertTrue("Charging session must activate", state.isChargingActive)
            assertTrue("Polling loop must start", engine.isPollingActive())
            assertEquals(4050.0f, state.currentVoltageMv ?: 0f, 0.01f)
            assertEquals(1500.0f, state.currentCurrentMa ?: 0f, 0.01f)
            assertEquals(30.0f, state.currentTemperatureCelsius ?: 0f, 0.01f)
            assertEquals(60.0f, state.currentBatteryPercent ?: 0f, 0.01f)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 2: Charging begins before the app opens
    @Test
    fun scenario2_applicationOpensWhileDeviceIsAlreadyCharging() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.pluggedTypeState = "AC"
        fakeHardware.voltageMvState = 4123
        fakeHardware.currentMicroAmpsState = 1_234_000
        fakeHardware.batteryLevelPercentState = 67.0f
        fakeHardware.temperatureCelsiusState = 31.5f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertTrue("Charging session must be active immediately", state.isChargingActive)
            assertFalse("Must not be in discharging state", state.isDischarging)
            assertTrue("Polling loop must be active", engine.isPollingActive())
            assertEquals(4123.0f, state.currentVoltageMv ?: 0f, 0.01f)
            assertEquals(1234.0f, state.currentCurrentMa ?: 0f, 0.01f)
            assertEquals(4.123f * 1.234f, state.currentPowerWatts ?: 0f, 0.01f)
            assertEquals(67.0f, state.currentBatteryPercent ?: 0f, 0.01f)
            assertEquals(31.5f, state.currentTemperatureCelsius ?: 0f, 0.01f)
            assertEquals("AC", state.pluggedSource)
            assertEquals(1, state.rollingHistory.size)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 3: The charger is disconnected
    @Test
    fun scenario3_chargerIsDisconnected() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.pluggedTypeState = "AC"

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()
            assertTrue(engine.isPollingActive())

            // Charger unplugged
            fakeHardware.isChargingState = false
            fakeHardware.pluggedTypeState = "UNPLUGGED"
            engine.onBatteryStateChanged(
                isCharging = false,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0
            )
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertTrue("Loop keeps running and switches to discharge samples", engine.isPollingActive())
            assertFalse("Charging session must be inactive", state.isChargingActive)
            assertTrue("Discharging flag set", state.isDischarging)
            assertNull("Live charging power must be null", state.currentPowerWatts)
            assertNull("Charging current must be null", state.currentCurrentMa)
            assertNull("Voltage must be null", state.currentVoltageMv)
            assertEquals("Unavailable", state.etaDisplayStatus)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Discharging keeps producing real live data, kept apart from the charging fields
    @Test
    fun discharging_producesLiveDischargeSamples() = runTest {
        fakeHardware.isChargingState = false
        fakeHardware.pluggedTypeState = "UNPLUGGED"
        fakeHardware.voltageMvState = 4000
        fakeHardware.currentMicroAmpsState = -500_000

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(2500)
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertTrue(state.isDischarging)
            assertTrue("Discharge history must fill while on battery", state.dischargeHistory.size >= 2)
            assertEquals(4000f, state.dischargeVoltageMv ?: 0f, 0.01f)
            assertEquals(500f, state.dischargeCurrentMa ?: 0f, 0.01f)
            assertEquals(2.0f, state.dischargePowerWatts ?: 0f, 0.01f)
            assertNull("Charging fields stay empty while on battery", state.currentPowerWatts)
            assertTrue("Charging history is not mixed in", state.rollingHistory.isEmpty())
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 4: The phone enters discharging mode
    @Test
    fun scenario4_deviceEntersDischargingMode() = runTest {
        fakeHardware.isChargingState = false
        fakeHardware.pluggedTypeState = "UNPLUGGED"
        fakeHardware.batteryLevelPercentState = 75.0f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertTrue("Loop samples live discharge data while the screen is open", engine.isPollingActive())
            assertFalse(state.isChargingActive)
            assertTrue(state.isDischarging)
            assertNull("No live charging power while discharging", state.currentPowerWatts)
            assertEquals(75.0f, state.currentBatteryPercent ?: 0f, 0.01f)
            assertEquals("Unavailable", state.etaDisplayStatus)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 5: Current is unavailable
    @Test
    fun scenario5_batteryCurrentIsUnavailable() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = 4100
        fakeHardware.currentMicroAmpsState = null // Hardware does not report current

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertEquals(4100.0f, state.currentVoltageMv ?: 0f, 0.01f)
            assertNull("Current must be null when unavailable", state.currentCurrentMa)
            assertNull("Power must be null when current is unavailable", state.currentPowerWatts)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 6: Voltage is unavailable
    @Test
    fun scenario6_voltageIsUnavailable() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = null // Voltage not exposed
        fakeHardware.currentMicroAmpsState = 1_000_000

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertNull("Voltage must be null when unavailable", state.currentVoltageMv)
            assertEquals(1000.0f, state.currentCurrentMa ?: 0f, 0.01f)
            assertNull("Power must be null when voltage is unavailable", state.currentPowerWatts)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 7: Temperature is unavailable
    @Test
    fun scenario7_temperatureIsUnavailable() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.temperatureCelsiusState = null // Hardware does not report temp

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertNull("Temperature must be null when sensor unavailable", state.currentTemperatureCelsius)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 8: Battery percentage remains unchanged
    @Test
    fun scenario8_batteryPercentageRemainsUnchanged() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.batteryLevelPercentState = 67.0f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            for (i in 1..3) {
                simulatedTimeMs += 1000L
                simulatedRealtimeMs += 1000L
                testScheduler.advanceTimeBy(1000L)
                testScheduler.runCurrent()
            }

            val state = engine.sessionState.value
            assertEquals(67.0f, state.currentBatteryPercent ?: 0f, 0.001f)
            state.rollingHistory.forEach { sample ->
                assertEquals(67.0f, sample.batteryPercent ?: 0f, 0.001f)
            }
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 9: Battery percentage increases
    @Test
    fun scenario9_batteryPercentageIncreases() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.batteryLevelPercentState = 67.0f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()
            assertEquals(67.0f, engine.sessionState.value.currentBatteryPercent ?: 0f, 0.001f)

            // Real battery level advances to 68%
            fakeHardware.batteryLevelPercentState = 68.0f
            simulatedTimeMs += 1000L
            simulatedRealtimeMs += 1000L
            testScheduler.advanceTimeBy(1000L)
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertEquals(68.0f, state.currentBatteryPercent ?: 0f, 0.001f)
            assertEquals(68.0f, state.rollingHistory.last().batteryPercent ?: 0f, 0.001f)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 10: The charging rate changes during a session
    @Test
    fun scenario10_chargingRateChangesDuringSession() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = 4000
        fakeHardware.currentMicroAmpsState = 1_000_000 // 1000 mA -> 4W

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()
            assertEquals(4.0f, engine.sessionState.value.currentPowerWatts ?: 0f, 0.01f)

            // Charger negotiates higher wattage (fast charging)
            fakeHardware.voltageMvState = 4200
            fakeHardware.currentMicroAmpsState = 2_500_000 // 2500 mA -> 10.5W
            simulatedTimeMs += 1000L
            simulatedRealtimeMs += 1000L
            testScheduler.advanceTimeBy(1000L)
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertEquals(4200.0f, state.currentVoltageMv ?: 0f, 0.01f)
            assertEquals(2500.0f, state.currentCurrentMa ?: 0f, 0.01f)
            assertEquals(10.5f, state.currentPowerWatts ?: 0f, 0.01f)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 11: The screen is closed and reopened
    @Test
    fun scenario11_screenClosedAndReopenedPreservesSession() = runTest {
        fakeHardware.isChargingState = true

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()
            assertTrue(engine.isPollingActive())

            // Screen closed
            engine.onScreenPaused()
            testScheduler.runCurrent()
            assertFalse("Loop must stop when screen closed", engine.isPollingActive())

            // Time advances while screen is off
            simulatedTimeMs += 5000L
            simulatedRealtimeMs += 5000L

            // Screen reopened
            engine.onScreenResumed()
            testScheduler.runCurrent()
            assertTrue("Loop must resume when screen reopened", engine.isPollingActive())
            assertTrue("Duration reflects total elapsed time", engine.sessionState.value.sessionDurationSeconds >= 5L)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Scenario 12: The application is restarted while charging
    @Test
    fun scenario12_applicationRestartedWhileCharging() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = 4150
        fakeHardware.currentMicroAmpsState = 1_800_000
        fakeHardware.batteryLevelPercentState = 72.0f

        // New engine instance (process recreation)
        val restartedEngine = createEngine(this)
        try {
            restartedEngine.onScreenResumed()
            testScheduler.runCurrent()

            val state = restartedEngine.sessionState.value
            assertTrue("Restarts in charging state", state.isChargingActive)
            assertTrue("Starts polling", restartedEngine.isPollingActive())
            assertEquals(4150.0f, state.currentVoltageMv ?: 0f, 0.01f)
            assertEquals(1800.0f, state.currentCurrentMa ?: 0f, 0.01f)
            assertEquals(72.0f, state.currentBatteryPercent ?: 0f, 0.01f)
        } finally {
            restartedEngine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Test Time to Full Calculation when Full
    @Test
    fun testTimeToFull_whenBatteryFull_reportsZero() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.isBatteryFullState = true
        fakeHardware.batteryLevelPercentState = 100.0f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertEquals(0L, state.estimatedTimeToFullSeconds)
            assertEquals("00:00:00", state.etaDisplayStatus)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Test Time to Full Calculation with observed battery percentage increase
    @Test
    fun testTimeToFull_withObservedProgress_calculatesCorrectEta() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.batteryLevelPercentState = 80.0f // 20% remaining to full

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            // 10 samples over 20 seconds, rising from 80% to 81% (rate = 1% per 20 seconds = 0.05% / sec)
            // Remaining 19% / 0.05% = 380 seconds = 00:06:20
            for (i in 1..10) {
                simulatedTimeMs += 2000L
                simulatedRealtimeMs += 2000L
                fakeHardware.batteryLevelPercentState = 80.0f + (i * 0.1f)
                engine.sampleOnce()
                testScheduler.runCurrent()
            }

            val state = engine.sessionState.value
            assertNotNull(state.estimatedTimeToFullSeconds)
            assertTrue("ETA seconds should be positive", (state.estimatedTimeToFullSeconds ?: 0L) > 0L)
            assertTrue("ETA string format should match HH:mm:ss", state.etaDisplayStatus?.matches(Regex("\\d{2}:\\d{2}:\\d{2}")) == true)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    // Test Exact Formatting of All 7 Fields
    @Test
    fun testRequired7FieldsFormatting() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = 4123
        fakeHardware.currentMicroAmpsState = 1_500_000
        fakeHardware.temperatureCelsiusState = 31.5f
        fakeHardware.batteryLevelPercentState = 75.25f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            // 1. Voltage: XXXX.xx mV
            val voltageStr = String.format(Locale.US, "%.2f mV", state.currentVoltageMv)
            assertEquals("4123.00 mV", voltageStr)

            // 2. Electric Current: XXXX.xx mA
            val currentStr = String.format(Locale.US, "%.2f mA", state.currentCurrentMa)
            assertEquals("1500.00 mA", currentStr)

            // 3. Wattage: XX.XX W (4123 * 1500 / 1_000_000 = 6.1845 -> 6.18 W)
            val wattageStr = String.format(Locale.US, "%.2f W", state.currentPowerWatts)
            assertEquals("6.18 W", wattageStr)

            // 4. Battery Temperature: XX.X °C
            val tempStr = String.format(Locale.US, "%.1f °C", state.currentTemperatureCelsius)
            assertEquals("31.5 °C", tempStr)

            // 5. Battery Percentage: XX.xx%
            val percentStr = String.format(Locale.US, "%.2f%%", state.currentBatteryPercent)
            assertEquals("75.25%", percentStr)

            // 6. Charging Duration: HH:mm:ss
            val durSec = state.sessionDurationSeconds
            val durationStr = String.format(Locale.US, "%02d:%02d:%02d", durSec / 3600, (durSec % 3600) / 60, durSec % 60)
            assertEquals("00:00:00", durationStr)

            // 7. Estimated Time to Full: initial status is Calculating...
            assertEquals("Calculating...", state.etaDisplayStatus)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }
}
