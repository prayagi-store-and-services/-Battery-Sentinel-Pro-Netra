package com.example

import com.example.model.CanonicalChargingSpeed
import com.example.model.MobileNetworkGeneration
import com.example.model.NetworkOptimizationState
import com.example.model.NetworkOptimizationStatus
import com.example.service.ChargingSpeedEngine
import com.example.service.NetworkModeSwitcher
import com.example.service.NetworkOptimizationEngine
import com.example.service.TelephonyStatusProvider
import com.example.service.TrafficMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkOptimizationEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockTraffic: MockTrafficMonitor
    private lateinit var mockTelephony: MockTelephonyProvider
    private lateinit var mockSwitcher: MockModeSwitcher
    private lateinit var engine: NetworkOptimizationEngine
    private var lastEmittedState: NetworkOptimizationState? = null

    class MockTrafficMonitor : TrafficMonitor {
        var trafficRate: Long? = 500_000L // 500 KB/s (< 2 MB/s)
        var fresh: Boolean = true
        var activeTransfer: Boolean = false

        override fun getRollingTrafficBytesPerSec(): Long? = trafficRate
        override fun isTrafficDataFresh(): Boolean = fresh
        override fun hasActiveTransfer(): Boolean = activeTransfer
    }

    class MockTelephonyProvider : TelephonyStatusProvider {
        var currentGen: MobileNetworkGeneration = MobileNetworkGeneration.FIVE_G
        var simReady: Boolean = true
        var serviceRegistered: Boolean = true
        var inCall: Boolean = false
        var supports3G: Boolean = false

        override fun getNetworkGeneration(): MobileNetworkGeneration = currentGen
        override fun isSimReady(): Boolean = simReady
        override fun isServiceRegistered(): Boolean = serviceRegistered
        override fun isInActiveCall(): Boolean = inCall
        override fun is3GSupported(): Boolean = supports3G
    }

    class MockModeSwitcher : NetworkModeSwitcher {
        var supported: Boolean = true
        var permitted: Boolean = true
        var preferredGen: MobileNetworkGeneration = MobileNetworkGeneration.FIVE_G
        var switchCallsCount = 0
        var lastSwitchedTarget: MobileNetworkGeneration? = null

        override fun isSwitchingSupported(): Boolean = supported
        override fun hasRequiredPermission(): Boolean = permitted
        override fun switchToGeneration(target: MobileNetworkGeneration): Boolean {
            switchCallsCount++
            lastSwitchedTarget = target
            preferredGen = target
            return true
        }
        override fun getCurrentPreferredGeneration(): MobileNetworkGeneration = preferredGen
    }

    @Before
    fun setUp() {
        mockTraffic = MockTrafficMonitor()
        mockTelephony = MockTelephonyProvider()
        mockSwitcher = MockModeSwitcher()
        lastEmittedState = null

        engine = NetworkOptimizationEngine(
            trafficMonitor = mockTraffic,
            telephonyStatusProvider = mockTelephony,
            modeSwitcher = mockSwitcher,
            scope = testScope,
            onStateUpdated = { state -> lastEmittedState = state }
        )
    }

    // 1. Screen-off with 5G and traffic below threshold.
    @Test
    fun test1_screenOffWith5GAndTrafficBelowThreshold() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G
        mockTraffic.trafficRate = 500_000L // 500 KB/s (< 2 MB/s)
        mockTraffic.fresh = true
        mockTraffic.activeTransfer = false

        engine.performScreenOffEvaluation()

        val state = engine.currentState
        assertEquals(NetworkOptimizationStatus.OPTIMIZED_TO_4G, state.status)
        assertEquals(MobileNetworkGeneration.FOUR_G_LTE, state.targetGeneration)
        assertEquals(MobileNetworkGeneration.FIVE_G, state.originalGeneration)
        assertTrue(state.isOptimizationActive)
        assertEquals(1, mockSwitcher.switchCallsCount)
        assertEquals(MobileNetworkGeneration.FOUR_G_LTE, mockSwitcher.lastSwitchedTarget)
    }

    // 2. Screen-off with 5G and traffic at or above 2 MB/s.
    @Test
    fun test2_screenOffWith5GAndTrafficAtOrAbove2MBps() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G
        mockTraffic.trafficRate = 2_097_152L // Exactly 2 MB/s
        mockTraffic.fresh = true

        engine.performScreenOffEvaluation()

        val state = engine.currentState
        assertEquals(NetworkOptimizationStatus.HIGH_TRAFFIC_SKIPPED, state.status)
        assertFalse(state.isOptimizationActive)
        assertEquals(0, mockSwitcher.switchCallsCount)

        // Above threshold
        mockTraffic.trafficRate = 3_500_000L
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.HIGH_TRAFFIC_SKIPPED, engine.currentState.status)
    }

    // 3. Screen-off with 4G and traffic below threshold.
    @Test
    fun test3_screenOffWith4GAndTrafficBelowThreshold() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FOUR_G_LTE
        mockTraffic.trafficRate = 400_000L
        mockTraffic.fresh = true
        mockTelephony.supports3G = false // 3G unsupported on modern network

        engine.performScreenOffEvaluation()

        // 4G/LTE is lowest practical supported mode, do not downgrade further
        assertEquals(NetworkOptimizationStatus.IDLE, engine.currentState.status)
        assertFalse(engine.currentState.isOptimizationActive)
        assertEquals(0, mockSwitcher.switchCallsCount)

        // If 3G is supported and continuity preserved
        mockTelephony.supports3G = true
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.OPTIMIZED_TO_3G, engine.currentState.status)
        assertEquals(MobileNetworkGeneration.THREE_G, engine.currentState.targetGeneration)
        assertTrue(engine.currentState.isOptimizationActive)
    }

    // 4. Unknown or stale traffic measurements.
    @Test
    fun test4_unknownOrStaleTrafficMeasurements() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G

        // Case A: Null / TrafficStats unsupported
        mockTraffic.trafficRate = null
        mockTraffic.fresh = true
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.UNRELIABLE_TRAFFIC_SKIPPED, engine.currentState.status)
        assertFalse(engine.currentState.isOptimizationActive)

        // Case B: Stale traffic measurement (> 30s)
        mockTraffic.trafficRate = 300_000L
        mockTraffic.fresh = false
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.UNRELIABLE_TRAFFIC_SKIPPED, engine.currentState.status)
        assertFalse(engine.currentState.isOptimizationActive)
    }

    // 5. SIM not ready or service not registered.
    @Test
    fun test5_simNotReadyOrServiceNotRegistered() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G

        // Case A: SIM not ready
        mockTelephony.simReady = false
        mockTelephony.serviceRegistered = true
        mockTelephony.inCall = false
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.SIM_NOT_READY_SKIPPED, engine.currentState.status)

        // Case B: Service not registered / out of service
        mockTelephony.simReady = true
        mockTelephony.serviceRegistered = false
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.SIM_NOT_READY_SKIPPED, engine.currentState.status)

        // Case C: Active voice call in progress (must preserve emergency / call continuity)
        mockTelephony.simReady = true
        mockTelephony.serviceRegistered = true
        mockTelephony.inCall = true
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.SIM_NOT_READY_SKIPPED, engine.currentState.status)
    }

    // 6. Active transfer detected just before switching.
    @Test
    fun test6_activeTransferDetectedJustBeforeSwitching() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G
        mockTraffic.trafficRate = 1_000_000L // 1 MB/s (< 2 MB/s)
        mockTraffic.fresh = true
        mockTraffic.activeTransfer = true // Active transfer detected right before switch

        engine.performScreenOffEvaluation()

        assertEquals(NetworkOptimizationStatus.ACTIVE_TRANSFER_SKIPPED, engine.currentState.status)
        assertFalse(engine.currentState.isOptimizationActive)
        assertEquals(0, mockSwitcher.switchCallsCount)
    }

    // 7. Unsupported network-mode switching.
    @Test
    fun test7_unsupportedNetworkModeSwitching() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G

        // Case A: Device hardware unsupported (no telephony)
        mockSwitcher.supported = false
        mockSwitcher.permitted = false
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.UNSUPPORTED_SKIPPED, engine.currentState.status)

        // Case B: Permission required (standard Android 3rd party app lacking MODIFY_PHONE_STATE)
        mockSwitcher.supported = true
        mockSwitcher.permitted = false
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.PERMISSION_REQUIRED_SKIPPED, engine.currentState.status)
        assertNotNull(engine.currentState.limitationNotice)
        assertFalse(engine.currentState.isOptimizationActive)
    }

    // 8. Screen turns on before a pending change.
    @Test
    fun test8_screenTurnsOnBeforePendingChange() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G

        // Screen turned off with 5000ms debounce
        engine.onScreenTurnedOff(debounceMs = 5000L)
        advanceTimeBy(2000L) // only 2 seconds pass

        assertEquals(NetworkOptimizationStatus.PENDING_SWITCH, engine.currentState.status)
        assertTrue(engine.currentState.isEvaluating)

        // Screen turns back on before debounce completes!
        engine.onScreenTurnedOn()
        advanceUntilIdle()

        // Debounce cancelled, no switch performed
        assertEquals(NetworkOptimizationStatus.IDLE, engine.currentState.status)
        assertFalse(engine.currentState.isEvaluating)
        assertFalse(engine.currentState.isOptimizationActive)
        assertEquals(0, mockSwitcher.switchCallsCount)
    }

    // 9. Restoration after a successful change.
    @Test
    fun test9_restorationAfterSuccessfulChange() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G
        mockTraffic.trafficRate = 500_000L
        mockTraffic.fresh = true

        // 1. Optimize
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.OPTIMIZED_TO_4G, engine.currentState.status)
        assertTrue(engine.currentState.isOptimizationActive)

        // Simulate network now reporting 4G
        mockTelephony.currentGen = MobileNetworkGeneration.FOUR_G_LTE

        // 2. Screen turns on -> restore original 5G
        engine.onScreenTurnedOn()
        advanceUntilIdle()

        assertEquals(NetworkOptimizationStatus.RESTORED, engine.currentState.status)
        assertEquals(MobileNetworkGeneration.FIVE_G, engine.currentState.currentGeneration)
        assertFalse(engine.currentState.isOptimizationActive)
        assertEquals(2, mockSwitcher.switchCallsCount) // 1 switch + 1 restore
        assertEquals(MobileNetworkGeneration.FIVE_G, mockSwitcher.lastSwitchedTarget)
    }

    // 10. User changes network mode before restoration.
    @Test
    fun test10_userChangesNetworkModeBeforeRestoration() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G

        // 1. Optimize to 4G
        engine.performScreenOffEvaluation()
        assertEquals(NetworkOptimizationStatus.OPTIMIZED_TO_4G, engine.currentState.status)
        assertTrue(engine.currentState.isOptimizationActive)

        // 2. User manually selects 3G or 2G in system settings while screen was off
        mockTelephony.currentGen = MobileNetworkGeneration.THREE_G

        // 3. Screen turns on -> NEVER overwrite user choice!
        engine.onScreenTurnedOn()
        advanceUntilIdle()

        assertEquals(NetworkOptimizationStatus.USER_CHANGED_PRESERVED, engine.currentState.status)
        assertEquals(MobileNetworkGeneration.THREE_G, engine.currentState.currentGeneration)
        assertFalse(engine.currentState.isOptimizationActive)
        // No restoration switch attempted
        assertEquals(1, mockSwitcher.switchCallsCount)
    }

    // 11. Repeated screen-off/on transitions without duplicate operations.
    @Test
    fun test11_repeatedScreenOffOnTransitionsWithoutDuplicateOperations() = testScope.runTest {
        mockTelephony.currentGen = MobileNetworkGeneration.FIVE_G

        // Rapid toggle 10 times
        for (i in 1..10) {
            engine.onScreenTurnedOff(debounceMs = 5000L)
            advanceTimeBy(500L)
            engine.onScreenTurnedOn()
            advanceTimeBy(500L)
        }
        advanceUntilIdle()

        // Zero switch calls executed because each was cancelled cleanly
        assertEquals(0, mockSwitcher.switchCallsCount)
        assertEquals(NetworkOptimizationStatus.IDLE, engine.currentState.status)
        assertFalse(engine.currentState.isOptimizationActive)
    }

    // 12. No regression in charging-speed detection.
    @Test
    fun test12_noRegressionInChargingSpeedDetection() {
        val speedEngine = ChargingSpeedEngine()

        // 4W -> Slow (< 5W)
        val slow = speedEngine.calculate(isCharging = true, voltageMv = 4000, currentMa = 1000)
        assertEquals(CanonicalChargingSpeed.SLOW, slow.speedCategory)
        assertEquals(4.0f, slow.rawPowerWatts ?: 0f, 0.01f)

        // 6W -> Normal (5W to < 10W)
        val normal = speedEngine.calculate(isCharging = true, voltageMv = 4000, currentMa = 1500)
        assertEquals(CanonicalChargingSpeed.NORMAL, normal.speedCategory)

        // 12W -> Fast (10W to < 20W)
        val fast = speedEngine.calculate(isCharging = true, voltageMv = 4000, currentMa = 3000)
        assertEquals(CanonicalChargingSpeed.FAST, fast.speedCategory)

        // 24W -> Super Fast (20W to < 40W)
        val superFast = speedEngine.calculate(isCharging = true, voltageMv = 4000, currentMa = 6000)
        assertEquals(CanonicalChargingSpeed.SUPER_FAST, superFast.speedCategory)

        // 40W -> Ultra Fast (>= 40W)
        val ultraFast = speedEngine.calculate(isCharging = true, voltageMv = 4000, currentMa = 10000)
        assertEquals(CanonicalChargingSpeed.ULTRA_FAST, ultraFast.speedCategory)

        // Discharging -> Unavailable charging speed, strictly independent power
        val discharging = speedEngine.calculate(isCharging = false, voltageMv = 3800, currentMa = -500)
        assertEquals(CanonicalChargingSpeed.UNAVAILABLE, discharging.speedCategory)
    }
}
