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

class FakeBatteryHardwareProvider(
    var isChargingState: Boolean = false,
    var pluggedTypeState: String = "UNPLUGGED",
    var voltageMvState: Int? = 4123,
    var currentMicroAmpsState: Int? = 1_234_000,
    var batteryLevelPercentState: Float? = 67.0f
) : BatteryHardwareProvider {
    override fun isCharging(): Boolean = isChargingState
    override fun getPluggedType(): String = pluggedTypeState
    override fun getVoltageMv(): Int? = voltageMvState
    override fun getCurrentMicroAmps(): Int? = currentMicroAmpsState
    override fun getBatteryLevelPercent(): Float? = batteryLevelPercentState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveChargingSessionEngineTest {

    private lateinit var fakeHardware: FakeBatteryHardwareProvider
    private var simulatedTimeMs = 1_000_000L

    @Before
    fun setup() {
        fakeHardware = FakeBatteryHardwareProvider()
        simulatedTimeMs = 1_000_000L
    }

    private fun createEngine(testScope: TestScope): LiveChargingSessionEngine {
        return LiveChargingSessionEngine(
            hardwareProvider = fakeHardware,
            scope = testScope,
            clock = { simulatedTimeMs },
            pollingIntervalMs = 1000L
        )
    }

    @Test
    fun scenario1_applicationOpensWhileDeviceIsCharging() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.pluggedTypeState = "AC"
        fakeHardware.voltageMvState = 4123
        fakeHardware.currentMicroAmpsState = 1_234_000
        fakeHardware.batteryLevelPercentState = 67.0f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertTrue("Charging session must be active", state.isChargingActive)
            assertFalse("Must not be in discharging state", state.isDischarging)
            assertTrue("Polling loop must be active", engine.isPollingActive())
            assertEquals(4.123f, state.currentVoltageV ?: 0f, 0.001f)
            assertEquals(1.234f, state.currentCurrentA ?: 0f, 0.001f)
            assertEquals(4.123f * 1.234f, state.currentPowerWatts ?: 0f, 0.01f)
            assertEquals(67.0f, state.currentBatteryPercent ?: 0f, 0.01f)
            assertEquals("AC", state.pluggedSource)
            assertEquals(1, state.rollingHistory.size)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario2_chargingBeginsWhileApplicationIsOpen() = runTest {
        // App opens while unplugged / discharging
        fakeHardware.isChargingState = false
        fakeHardware.pluggedTypeState = "UNPLUGGED"

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            assertFalse("Polling loop must not run while discharging", engine.isPollingActive())
            assertFalse(engine.sessionState.value.isChargingActive)

            // Charger plugged in
            fakeHardware.isChargingState = true
            fakeHardware.pluggedTypeState = "USB"
            fakeHardware.voltageMvState = 4050
            fakeHardware.currentMicroAmpsState = 1_500_000

            engine.onBatteryStateChanged(
                isCharging = true,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                plugged = BatteryManager.BATTERY_PLUGGED_USB
            )
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertTrue("Charging session must activate", state.isChargingActive)
            assertTrue("Polling loop must start", engine.isPollingActive())
            assertEquals(4.050f, state.currentVoltageV ?: 0f, 0.001f)
            assertEquals(1.500f, state.currentCurrentA ?: 0f, 0.001f)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario3_batteryPercentageRemainsUnchangedForSeveralUpdates() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.batteryLevelPercentState = 67.0f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            // Advance 3 seconds of polling
            for (i in 1..3) {
                simulatedTimeMs += 1000L
                testScheduler.advanceTimeBy(1000L)
                testScheduler.runCurrent()
            }

            val state = engine.sessionState.value
            // Percentage must remain strictly 67.0%, never falsely incremented
            assertEquals(67.0f, state.currentBatteryPercent ?: 0f, 0.001f)
            assertTrue("Samples must accumulate", state.rollingHistory.size >= 4)
            state.rollingHistory.forEach { sample ->
                assertEquals(67.0f, sample.batteryPercent ?: 0f, 0.001f)
            }
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario4_batteryPercentageIncreases() = runTest {
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

    @Test
    fun scenario5_chargerIsDisconnected() = runTest {
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
            assertFalse("Polling loop must terminate immediately", engine.isPollingActive())
            assertFalse("Charging session must be inactive", state.isChargingActive)
            assertTrue("Discharging flag set", state.isDischarging)
            assertNull("Live charging power must be null", state.currentPowerWatts)
            assertNull("Charging current must be null", state.currentCurrentA)
            assertFalse("History buffer preserved", state.rollingHistory.isEmpty())
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario6_deviceEntersDischargingMode() = runTest {
        fakeHardware.isChargingState = false
        fakeHardware.pluggedTypeState = "UNPLUGGED"
        fakeHardware.batteryLevelPercentState = 75.0f

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertFalse("Loop must never start while discharging", engine.isPollingActive())
            assertFalse(state.isChargingActive)
            assertTrue(state.isDischarging)
            assertNull("No live charging power while discharging", state.currentPowerWatts)
            assertEquals(75.0f, state.currentBatteryPercent ?: 0f, 0.01f)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario7_batteryCurrentIsUnavailable() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = 4100
        fakeHardware.currentMicroAmpsState = null // Hardware does not report current

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertEquals(4.100f, state.currentVoltageV ?: 0f, 0.001f)
            assertNull("Current must be null when unavailable", state.currentCurrentA)
            assertNull("Power must be null when current is unavailable", state.currentPowerWatts)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario8_voltageIsUnavailable() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = null // Voltage not exposed
        fakeHardware.currentMicroAmpsState = 1_000_000

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertNull("Voltage must be null when unavailable", state.currentVoltageV)
            assertEquals(1.000f, state.currentCurrentA ?: 0f, 0.001f)
            assertNull("Power must be null when voltage is unavailable", state.currentPowerWatts)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario9_currentReadingsAreInvalidOrInconsistent() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = 4100
        // Absurd 50A current (corrupt sensor reading)
        fakeHardware.currentMicroAmpsState = 50_000_000

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val state = engine.sessionState.value
            assertNull("Absurd current reading must be discarded as null", state.currentCurrentA)
            assertNull("Power calculation must be null for invalid current", state.currentPowerWatts)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario10_userSwitchesAwayFromMonitoringScreen() = runTest {
        fakeHardware.isChargingState = true

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()
            assertTrue("Loop is active on screen", engine.isPollingActive())

            // User switches tab or leaves screen
            engine.onScreenPaused()
            testScheduler.runCurrent()

            assertFalse("Polling loop must stop immediately when screen paused", engine.isPollingActive())
            assertFalse(engine.isScreenActiveForTesting())
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario11_userReturnsToMonitoringScreen() = runTest {
        fakeHardware.isChargingState = true

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            engine.onScreenPaused()
            testScheduler.runCurrent()
            assertFalse(engine.isPollingActive())

            // User returns to screen
            engine.onScreenResumed()
            testScheduler.runCurrent()

            assertTrue("Loop must restart when user returns while charging", engine.isPollingActive())
            assertTrue(engine.sessionState.value.isChargingActive)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun scenario12_applicationIsClosedDuringChargingSession() = runTest {
        fakeHardware.isChargingState = true

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()
            assertTrue(engine.isPollingActive())

            // App closed / activity destroyed -> onScreenPaused called
            engine.onScreenPaused()
            testScheduler.runCurrent()

            assertFalse("Polling job cancelled, no leak", engine.isPollingActive())
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun testRollingHistoryBoundedAt300Samples() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = 4000
        fakeHardware.currentMicroAmpsState = 1_000_000

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            // Trigger 350 samples
            for (i in 1..350) {
                simulatedTimeMs += 1000L
                engine.sampleOnce()
                testScheduler.runCurrent()
            }

            val history = engine.sessionState.value.rollingHistory
            assertEquals("History must be capped at 300 entries", 300, history.size)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun testPowerCalculationBelowOneWattUsesMilliWattsRange() = runTest {
        fakeHardware.isChargingState = true
        fakeHardware.voltageMvState = 4000 // 4.0 V
        fakeHardware.currentMicroAmpsState = 100_000 // 0.1 A -> 0.4 W = 400 mW

        val engine = createEngine(this)
        try {
            engine.onScreenResumed()
            testScheduler.runCurrent()

            val power = engine.sessionState.value.currentPowerWatts
            assertNotNull(power)
            assertEquals(0.40f, power!!, 0.001f)
            assertTrue("Power is below 1 Watt", power < 1.0f)
        } finally {
            engine.onScreenPaused()
            testScheduler.runCurrent()
        }
    }
}
