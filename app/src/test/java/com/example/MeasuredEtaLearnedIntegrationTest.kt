package com.example

import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.service.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic measured transitions, not physical charger/phone acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MeasuredEtaLearnedIntegrationTest {
    @Before fun trainPhoneCurve() {
        ChargeCurveLearner.resetForTesting(ApplicationProvider.getApplicationContext(), "eta_integration_" + System.nanoTime())
        var at = 1_000_000L
        repeat(2) {
            ChargeCurveLearner.observeTransition(70, BatteryManager.BATTERY_STATUS_CHARGING, 1, 25f, at)
            for (level in 71..100) {
                at += if (level > 80) 180_000 else 60_000
                ChargeCurveLearner.observeTransition(level, BatteryManager.BATTERY_STATUS_CHARGING, 1, 25f, at)
            }
        }
    }
    @After fun clearHistory() {
        ChargeCurveLearner.resetForTesting(ApplicationProvider.getApplicationContext(), "eta_empty_" + System.nanoTime())
    }
    @Test fun centralFullEstimateAndLiveTargetUseActualLearnerShape() = runTest {
        var now = 20_000_000L
        val center = NetraCentralDataCenter { now }
        val live = LivePowerTelemetry().apply { etaCurve = MeasuredEtaPolicy::learned }
        suspend fun read(level: Int, minute: Int, status: Int = BatteryManager.BATTERY_STATUS_CHARGING): String? {
            now = 20_000_000L + minute * 60_000L
            center.processRawInput(level = level, scale = 100, status = status, plugged = 1,
                temperatureRaw = 250, voltage = 4200, currentMicroAmps = 1_000_000,
                bluetoothConnected = null, bluetoothBattery = null)
            return live.update(BatterySnapshot(status, 1, level, 100, 4200, 250, 1_000_000), minute * 60_000L).estimate
        }
        for (level in 70..80 step 2) read(level, level - 70)
        read(81, 13)
        assertEquals("~00:54:00 (est.)", read(82, 16))
        assertEquals(54, center.centralState.value.chargingEtaMinutes)
        assertNull(center.centralState.value.dischargingEtaMinutes)
        live.targetPercent = 90
        assertEquals("~00:24:00 (est.)", read(82, 17))
        read(82, 18, BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        assertNull(center.centralState.value.chargingEtaMinutes)
    }
}
