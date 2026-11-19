package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepository
import com.example.model.CanonicalChargingSpeed
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ChargingEnergyAnnouncementTest {
  @Test fun settingDefaultsOnButSavedOffIsNeverReset() {
    val context=ApplicationProvider.getApplicationContext<Context>()
    assertTrue(com.example.data.repository.SentinelSettings().announceChargingSpeed)
    val repo=SettingsRepository(context)
    repo.setAnnounceChargingSpeed(false)
    assertFalse(SettingsRepository(context).settings.value.announceChargingSpeed)
    repo.setAnnouncePhoneBattery(true)
    assertFalse(repo.settings.value.announceChargingSpeed)
    repo.setAnnounceChargingSpeed(true)
  }
  @Test fun allSixPhrasesAreTypeOnlyAndMissingHasNoNormalFallback() {
    val cases=mapOf(CanonicalChargingSpeed.SLOW to "Slow charging",CanonicalChargingSpeed.NORMAL to "Normal charging",
      CanonicalChargingSpeed.FAST to "Fast charging",CanonicalChargingSpeed.ULTRA_FAST to "Ultra Fast charging",
      CanonicalChargingSpeed.HYPER_FAST to "Hyper Fast charging",CanonicalChargingSpeed.EXTREME_FAST to "Extreme Fast charging")
    for((speed,text) in cases)assertEquals(text,AnnouncementEngine.speedSpeech(speed))
    assertNull(AnnouncementEngine.speedSpeech(CanonicalChargingSpeed.UNAVAILABLE))
    assertNull(AnnouncementEngine.speedSpeech(CanonicalChargingSpeed.SUPER_FAST))
  }
  @Test fun invalidationDropsSupersededOrUnavailableSpeedButKeepsUnrelatedQueueItems() {
    val context=ApplicationProvider.getApplicationContext<Context>()
    val engine=AnnouncementEngine(context)
    val queueField=AnnouncementEngine::class.java.getDeclaredField("queue").apply{isAccessible=true}
    @Suppress("UNCHECKED_CAST") val queue=queueField.get(engine) as java.util.PriorityQueue<AnnouncementItem>
    queue.add(AnnouncementItem("old","Normal charging",AnnouncementPriority.CHARGING_SPEED,"CHARGING_SPEED",subjectKey="NORMAL"))
    queue.add(AnnouncementItem("new","Fast charging",AnnouncementPriority.CHARGING_SPEED,"CHARGING_SPEED",subjectKey="FAST"))
    queue.add(AnnouncementItem("urgent","Thermal warning",AnnouncementPriority.CRITICAL_THERMAL,"THERMAL"))
    val invalidate=AnnouncementEngine::class.java.getDeclaredMethod("invalidateSupersededSpeed",CanonicalChargingSpeed::class.java).apply{isAccessible=true}
    invalidate.invoke(engine,CanonicalChargingSpeed.FAST)
    assertFalse(queue.any{it.id=="old"})
    assertTrue(queue.any{it.id=="new"})
    assertTrue(queue.any{it.id=="urgent"})
    invalidate.invoke(engine,CanonicalChargingSpeed.UNAVAILABLE)
    assertEquals(listOf("urgent"),queue.map{it.id})
  }
}
