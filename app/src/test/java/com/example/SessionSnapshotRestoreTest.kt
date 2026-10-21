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
}
