package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.AlertDeliveryState
import com.example.service.AnnouncementEngine
import com.example.service.AnnouncementItem
import com.example.service.AnnouncementPriority
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnouncementRestartIntegrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun engine() = AnnouncementEngine(context)
    private fun item(category: String, text: String = category) = AnnouncementItem(
        "${category}_${System.nanoTime()}", text, AnnouncementPriority.CRITICAL_THERMAL, category, isNightException = true)
    private fun queued(engine: AnnouncementEngine): Int {
        val field = AnnouncementEngine::class.java.getDeclaredField("queue").apply { isAccessible = true }
        return (field.get(engine) as java.util.PriorityQueue<*>).size
    }
    @Before fun reset() {
        context.getSharedPreferences("netra_alert_delivery_v1",Context.MODE_PRIVATE).edit().clear().commit()
        NetraApplication.instance.settingsRepository.setAnnouncementsMasterEnabled(true)
    }
    @Test fun lowProtectionSpeechDoesNotReplayAfterEngineRecreation() {
        val first=engine();first.enqueue(item("LOW_BATTERY_PROTECTION"));assertEquals(1,queued(first))
        val next=engine();next.enqueue(item("LOW_BATTERY_PROTECTION"));assertEquals(0,queued(next))
    }
    @Test fun criticalEscalationIsNotSuppressedByPersistedWarning() {
        engine().enqueue(item("THERMAL_WARNING"))
        val next=engine();next.enqueue(item("THERMAL_CRITICAL"));assertEquals(1,queued(next))
    }
    @Test fun sameCriticalConditionCannotReplayWithDifferentText() {
        engine().enqueue(item("THERMAL_CRITICAL","Battery 46 C"))
        val next=engine();next.enqueue(item("THERMAL_CRITICAL","Battery 47 C"));assertEquals(0,queued(next))
    }
    @Test fun freshMeasuredRecoveryAllowsAnotherProtectionAttempt() {
        engine().enqueue(item("LOW_BATTERY_PROTECTION"))
        AlertDeliveryState(context).observeRecovery(35,null,true)
        val next=engine();next.enqueue(item("LOW_BATTERY_PROTECTION"));assertEquals(1,queued(next))
    }
    @Test fun disabledMasterDoesNotConsumeCondition() {
        NetraApplication.instance.settingsRepository.setAnnouncementsMasterEnabled(false)
        val first=engine();first.enqueue(item("LOW_BATTERY_PROTECTION"));assertEquals(0,queued(first))
        NetraApplication.instance.settingsRepository.setAnnouncementsMasterEnabled(true)
        val next=engine();next.enqueue(item("LOW_BATTERY_PROTECTION"));assertEquals(1,queued(next))
    }
}
