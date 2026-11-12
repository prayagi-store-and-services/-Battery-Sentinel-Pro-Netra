package com.example

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.service.CentralCapabilityRegistry
import com.example.service.NetraCentralDataCenter
import com.example.service.SessionSnapshot
import com.example.service.SessionSnapshotStore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionSnapshotRestoreTest {
    private class MemStore : SessionSnapshotStore {
        var v: SessionSnapshot? = null
        override fun load() = v
        override fun save(s: SessionSnapshot) { v = s }
    }

    private fun center(store: MemStore, clock: () -> Long): NetraCentralDataCenter {
        val context: Context = ApplicationProvider.getApplicationContext()
        return NetraCentralDataCenter(clock).also { c ->
            NetraCentralDataCenter::class.java.getDeclaredField("capabilityRegistry").apply {
                isAccessible = true
                set(c, CentralCapabilityRegistry(context))
            }
            c.refreshCapabilities()
            c.sessionStore = store
        }
    }

    private suspend fun onBattery(c: NetraCentralDataCenter) {
        c.processRawInput(60, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 300, 4000, -500_000, null, null)
    }

    @Test
    fun onBatterySinceSurvivesRestart() = runTest {
        val store = MemStore()
        var now = 1_000_000L
        val first = center(store, { now })
        onBattery(first)
        val started = first.centralState.value.dischargingStartedAt
        assertNotNull(started)
        // The app is updated 6 hours later: a brand new data center starts with the same store.
        now += 6 * 3_600_000L
        val second = center(store, { now })
        onBattery(second)
        assertEquals(started, second.centralState.value.dischargingStartedAt)
        assertNotEquals(now, second.centralState.value.dischargingStartedAt)
    }

    @Test
    fun chargerPluggedWhileAppWasDeadStartsANewSession() = runTest {
        val store = MemStore()
        var now = 1_000_000L
        val first = center(store, { now })
        onBattery(first)
        now += 3_600_000L
        val second = center(store, { now })
        second.processRawInput(61, 100, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 300, 4000, 1_000_000, null, null)
        assertEquals(now, second.centralState.value.chargingStartedAt)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun bothLiveTimerEnginesUseRestoredCentralIdentity() = runTest {
        for (charging in listOf(false, true)) {
            val store = MemStore()
            var now = 1_000_000L
            suspend fun sample(c: NetraCentralDataCenter) {
                c.processRawInput(60, 100,
                    if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING,
                    if (charging) BatteryManager.BATTERY_PLUGGED_AC else 0,
                    300, 4000, 1_000_000, null, null)
            }
            sample(center(store) { now })
            now += 6 * 3_600_000L
            val restored = center(store) { now }
            sample(restored)
            val provider: (Boolean) -> Long? = { wantCharge ->
                com.example.service.CanonicalSessionTime.startEpochMs(restored.centralState.value, wantCharge)
            }
            val card = com.example.service.LivePowerTelemetry({ mode ->
                provider(mode == com.example.service.PowerMode.CHARGING)
            }, { now })
            val snapshot = com.example.service.BatterySnapshot(
                if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING,
                if (charging) 1 else 0, 60, 100, 4000, 300, 1_000_000)
            assertEquals("06:00:00", card.update(snapshot, 10_000L).sessionDuration)
            val monitor = com.example.service.LiveChargingSessionEngine(
                com.example.service.FakeBatteryHardwareProvider(isChargingState = charging), this,
                { now }, { 10_000L }, canonicalSessionStartEpochMs = provider)
            monitor.onScreenResumed()
            runCurrent()
            assertEquals(21_600L, if (charging) monitor.sessionState.value.sessionDurationSeconds
                else monitor.sessionState.value.dischargeDurationSeconds)
            monitor.onScreenPaused()
            runCurrent()
        }
    }

}
