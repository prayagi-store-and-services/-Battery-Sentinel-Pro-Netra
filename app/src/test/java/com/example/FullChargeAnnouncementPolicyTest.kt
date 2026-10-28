package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SentinelSettings
import com.example.model.NetraCentralEvent
import com.example.model.NetraEventType
import com.example.service.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.PriorityQueue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullChargeAnnouncementPolicyTest {
    private lateinit var context: Context
    private lateinit var app: NetraApplication
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        app = NetraApplication.instance
        context.getSharedPreferences("netra_full_observation_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Suppress("UNCHECKED_CAST")
    private fun settings(value: SentinelSettings) {
        val field = app.settingsRepository.javaClass.getDeclaredField("_settings").apply { isAccessible = true }
        (field.get(app.settingsRepository) as MutableStateFlow<SentinelSettings>).value = value
    }
    @Suppress("UNCHECKED_CAST")
    private fun queued(engine: AnnouncementEngine): List<AnnouncementItem> {
        val field = engine.javaClass.getDeclaredField("queue").apply { isAccessible = true }
        return (field.get(engine) as PriorityQueue<AnnouncementItem>).toList()
    }
    private fun deliver(engine: AnnouncementEngine, end: Long) {
        val method = engine.javaClass.getDeclaredMethod("handleCanonicalEvent", NetraCentralEvent::class.java).apply { isAccessible = true }
        method.invoke(engine, NetraCentralEvent("observed_$end", NetraEventType.CHARGER_DISCONNECTED,
            end, "true", "false", "TEST"))
    }
    private fun stage(end: Long) {
        FullChargeSummaryStore(context).save(FullChargeSummaryStore.Completed(end,
            FullChargeObservation.Summary(end - 60_000, 90, 60_000, 60_000)))
        app.centralDataCenter.initPersistence(context)
    }
    @Test fun masterOffMuteAndChargerOffConsumeWithoutCatchup() {
        val baseline = SentinelSettings(nightProtectionEnabled = false)
        val cases = listOf(baseline.copy(announcementsMasterEnabled = false),
            baseline.copy(announcementMutedUntilMs = System.currentTimeMillis() + 600_000),
            baseline.copy(announceChargerConnected = false))
        cases.forEachIndexed { index, setting ->
            val end = 2_000_000L + index * 1_000_000
            stage(end); settings(setting)
            val engine = AnnouncementEngine(context); deliver(engine, end)
            assertFalse(queued(engine).any { it.category == "FULL_OBSERVATION" })
            assertNull(app.centralDataCenter.consumeFullChargeSummary(end))
            settings(baseline); deliver(engine, end)
            assertFalse(queued(engine).any { it.category == "FULL_OBSERVATION" })
        }
    }
    @Test fun nightProtectionSuppressesSummaryEvenThoughBasicPlugAlertsAreExceptions() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        settings(SentinelSettings(nightProtectionEnabled = true, nightStartHour = hour, nightEndHour = (hour + 1) % 24))
        stage(2_000_000)
        val engine = AnnouncementEngine(context); deliver(engine, 2_000_000)
        assertFalse(queued(engine).any { it.category == "FULL_OBSERVATION" })
        assertNull(app.centralDataCenter.consumeFullChargeSummary(2_000_000))
    }
    @Test fun eligibleDisconnectQueuesOneNonExceptionSummary() {
        settings(SentinelSettings(nightProtectionEnabled = false))
        stage(2_000_000)
        val engine = AnnouncementEngine(context); deliver(engine, 2_000_000); deliver(engine, 2_000_000)
        val summaries = queued(engine).filter { it.category == "FULL_OBSERVATION" }
        assertEquals(1, summaries.size)
        assertFalse(summaries.single().isNightException)
        assertTrue(summaries.single().text.contains("observed intervals"))
    }
}
