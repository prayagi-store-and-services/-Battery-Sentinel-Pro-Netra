package com.example.data.repository

import com.example.data.local.*
import com.example.model.NetraCentralState
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BatteryRepositoryHistoryTest {
    private val rows = mutableListOf<ChargingSession>()
    private val logs = mutableListOf<ActivityLog>()
    private inline fun <reified T> dao(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        when (method.name) {
            "insert" -> { when (val value = args!![0]) { is ChargingSession -> rows.add(value); is ActivityLog -> logs.add(value) }; 1L }
            "insertBounded" -> { logs.add(args!![0] as ActivityLog); 1L }
            "getLatestRecord", "getLatestSession" -> flowOf(null)
            "getTotalRecordCount", "getTotalSessionsCount", "getOverheatEventCount", "getDeepDischargeCount" -> flowOf(0)
            "clearAll" -> kotlin.Unit
            else -> flowOf(emptyList<Any>())
        }
    } as T
    private fun repo() = BatteryRepository(dao<BatteryDao>(), dao<ChargingSessionDao>(), dao<ActivityLogDao>())
    private fun record(time: Long, level: Int, charging: Boolean = true, temp: Float = 30f) = BatteryRecord(timestamp=time, level=level, temperature=temp, voltageMv=4000, currentMa=1000, powerWatts=4f, isCharging=charging, pluggedType=if(charging) "AC" else "BATTERY", healthStatus="GOOD")
    private fun state(start: Long, stop: Long? = null) = NetraCentralState(isCharging=stop == null, chargingStartedAt=start, chargingStoppedAt=stop)

    @Test fun restartDoesNotBackdateCurrentLevelToSavedStart() = runBlocking {
        val r = repo(); r.recordTelemetryDebounced(record(3_600_000,70),state(1_000_000))
        r.recordTelemetryDebounced(record(3_720_000,75,false),state(1_000_000,3_720_000))
        assertEquals(3_600_000L, rows.single().startTime)
        assertEquals(70, rows.single().startLevel)
        assertEquals(2, rows.single().durationMinutes)
        assertTrue(logs.any { it.message.contains("partial",ignoreCase=true) })
    }
    @Test fun recreatedRepositoryDoesNotInventEarlierPeakOrLevel() = runBlocking {
        repo().recordTelemetryDebounced(record(1_000_000,20,temp=45f),state(1_000_000))
        val restored = repo(); restored.recordTelemetryDebounced(record(3_600_000,70),state(1_000_000))
        restored.recordTelemetryDebounced(record(3_720_000,75,false,temp=31f),state(1_000_000,3_720_000))
        assertEquals(70,rows.single().startLevel); assertEquals(31f,rows.single().peakTemperature)
        assertEquals(3_600_000L,rows.single().startTime)
    }
    @Test fun subMinuteObservationDoesNotBecomeOneMinute() = runBlocking {
        val r=repo();r.recordTelemetryDebounced(record(1_000_000,20),state(1_000_000))
        r.recordTelemetryDebounced(record(1_020_000,21,false),state(1_000_000,1_020_000))
        assertEquals(0,rows.single().durationMinutes)
    }
    @Test fun staleStopOnChargingStateDoesNotCloseNewObservation() = runBlocking {
        val r=repo();r.recordTelemetryDebounced(record(3_600_000,70),NetraCentralState(isCharging=true,chargingStartedAt=3_600_000,chargingStoppedAt=2_000_000))
        r.recordTelemetryDebounced(record(3_720_000,75,false),state(3_600_000,3_720_000))
        assertEquals(1,rows.size)
    }
    @Test fun duplicateDisconnectCannotInsertTwice() = runBlocking {
        val r=repo();r.recordTelemetryDebounced(record(1_000_000,20),state(1_000_000))
        r.recordTelemetryDebounced(record(1_120_000,25,false),state(1_000_000,1_120_000))
        r.recordTelemetryDebounced(record(1_130_000,26,false),state(1_000_000,1_120_000))
        assertEquals(1,rows.size)
    }
    @Test fun futureCanonicalStartCannotCreateObservation() = runBlocking {
        val r=repo();r.recordTelemetryDebounced(record(1_000_000,20),state(2_000_000))
        r.recordTelemetryDebounced(record(1_120_000,25,false),state(2_000_000,1_120_000))
        assertTrue(rows.isEmpty()); assertTrue(logs.isEmpty())
    }
    @Test fun restartingAlreadyDisconnectedCannotInventCompletedHistory() = runBlocking {
        repo().recordTelemetryDebounced(record(1_120_000,25,false),state(1_000_000,1_120_000))
        assertTrue(rows.isEmpty())
    }
    @Test fun futureStopCannotFabricateDuration() = runBlocking {
        val r=repo();r.recordTelemetryDebounced(record(1_000_000,20),state(1_000_000))
        r.recordTelemetryDebounced(record(1_120_000,25,false),state(1_000_000,2_000_000))
        assertTrue(rows.isEmpty())
    }

}
