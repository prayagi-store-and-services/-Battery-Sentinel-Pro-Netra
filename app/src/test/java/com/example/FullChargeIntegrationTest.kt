package com.example

import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.model.NetraEventType
import com.example.service.NetraCentralDataCenter
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FullChargeIntegrationTest {
    private var time = 0L
    private lateinit var context: Context
    private fun center() = NetraCentralDataCenter { 1_000_000 + time }.also { it.elapsedClock = { time }; it.initPersistence(context) }
    private suspend fun read(c: NetraCentralDataCenter, level: Int, connected: Boolean = true) = c.processRawInput(
        level, 100, if (connected) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING,
        if (connected) BatteryManager.BATTERY_PLUGGED_USB else 0, 300, 4000, 500_000, null, null)
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("netra_full_observation_v1", Context.MODE_PRIVATE).edit().clear().commit()
        com.example.service.ChargeCurveLearner.resetForTesting(context, "full_integration_curve")
    }
    @Test fun completionIsCommittedBeforeDisconnectDeliveryAndDuplicatesDoNotReplay() = runTest {
        val c = center()
        var deliveries = 0
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            c.centralEvents.collect { event ->
                if (event.eventType == NetraEventType.CHARGER_DISCONNECTED) {
                    deliveries++
                    assertNotNull(c.fullChargeSummary.value)
                    assertNotNull(c.consumeFullChargeSummary(event.timestamp))
                    assertNull(c.consumeFullChargeSummary(event.timestamp))
                }
            }
        }
        read(c, 90); time = 60_000; read(c, 100); time = 120_000; read(c, 100, false)
        assertEquals(1, deliveries)
        read(c, 100, false); assertEquals(1, deliveries)
        assertEquals(90, c.fullChargeSummary.value!!.observation.startPercent)
        assertEquals(c.fullChargeSummary.value, center().fullChargeSummary.value)
        job.cancel()
    }
    @Test fun unknownRawLevelCannotBorrowRetainedDisplayPercentage() = runTest {
        val c = center()
        read(c, 90); time = 10; read(c, -1); time = 20; read(c, 100); time = 30; read(c, 100, false)
        assertNull(c.fullChargeSummary.value)
    }
    @Test fun processRestartAtFullCannotInferCompletion() = runTest {
        read(center(), 90); time = 60_000
        val restarted = center(); read(restarted, 100); time = 120_000; read(restarted, 100, false)
        assertNull(restarted.fullChargeSummary.value)
    }
    @Test fun gapAtDisconnectCannotProduceStitchedSummary() = runTest {
        val c = center(); read(c, 90); time = 60_000; read(c, 100)
        time += 15 * 60_000 + 1; read(c, 100, false)
        assertNull(c.fullChargeSummary.value)
    }
}
