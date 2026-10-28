package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.FullChargeObservation
import com.example.service.FullChargeSummaryStore
import com.example.service.fullChargeSummarySpeech
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullChargeSummaryStoreTest {
    private lateinit var context: Context
    private fun value(end: Long = 1_120_000) = FullChargeSummaryStore.Completed(end,
        FullChargeObservation.Summary(end - 60_000, 90, 60_000, 60_000))
    @Before fun clear() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("netra_full_observation_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun completedRecordSurvivesRestartAndIsClaimedOnce() {
        val first = FullChargeSummaryStore(context)
        assertTrue(first.save(value()))
        val restarted = FullChargeSummaryStore(context)
        assertEquals(value(), restarted.load())
        assertNull(restarted.consume(value().disconnectedAt - 1))
        assertEquals(value(), restarted.consume(value().disconnectedAt))
        assertNull(FullChargeSummaryStore(context).consume(value().disconnectedAt))
        assertEquals(value(), restarted.load()) // UI history remains after speech claim.
    }
    @Test fun replugRecordCannotReplayOldDisconnect() {
        val store = FullChargeSummaryStore(context)
        store.save(value()); store.consume(value().disconnectedAt)
        val newer = value(2_000_000)
        assertTrue(store.save(newer))
        assertNull(store.consume(value().disconnectedAt))
        assertEquals(newer, store.consume(newer.disconnectedAt))
    }
    @Test fun invalidSaveCannotReplaceValidHistory() {
        val store = FullChargeSummaryStore(context)
        store.save(value())
        val invalid = listOf(value().copy(disconnectedAt = -1),
            value().copy(observation = value().observation.copy(startPercent = 100)),
            value().copy(observation = value().observation.copy(observedToFullMs = -1)),
            value().copy(observation = value().observation.copy(connectedAfterFullMs = FullChargeSummaryStore.MAX_INTERVAL + 1)),
            value().copy(observation = value().observation.copy(firstFullWallMs = 1)))
        invalid.forEach { assertFalse(store.save(it)); assertEquals(value(), store.load()) }
    }
    @Test fun corruptedPreferenceTypesAndTimelineFailClosed() {
        val store = FullChargeSummaryStore(context)
        val prefs = context.getSharedPreferences("netra_full_observation_v1", Context.MODE_PRIVATE)
        store.save(value()); prefs.edit().putString("full", "bad").commit()
        assertNull(store.load()); assertNull(store.consume(value().disconnectedAt))
        store.save(value()); prefs.edit().putLong("after", 900_000).commit()
        assertNull(store.load())
    }
    @Test fun fractionalMinuteSpeechNeverClaimsZeroMinutes() {
        val speech = fullChargeSummarySpeech(value().observation.copy(observedToFullMs = 500, connectedAfterFullMs = 0))
        assertTrue(speech.contains("less than one minute"))
        assertFalse(speech.contains("0 minutes"))
        assertTrue(speech.contains("not exact charging completion times"))
    }
}
