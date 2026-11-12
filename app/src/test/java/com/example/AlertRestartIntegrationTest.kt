package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NetraCentralState
import com.example.receiver.AlarmActionReceiver
import com.example.service.BatteryMonitorService
import com.example.service.TargetAlarmRepeat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class AlertRestartIntegrationTest {
    private val c get()=ApplicationProvider.getApplicationContext<Context>()
    private val nm get()=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun service()=Robolectric.buildService(BatteryMonitorService::class.java).get()
    private fun sample(temp:Float=30f,session:Long=System.currentTimeMillis()-60_000)=NetraCentralState(batteryLevel=85,isCharging=true,isChargerConnected=true,chargerConnectedAt=session,temperatureCelsius=temp,isDataFresh=true)
    @Before fun reset() {
        c.getSharedPreferences("netra_alert_delivery_v1",Context.MODE_PRIVATE).edit().clear().commit()
        TargetAlarmRepeat.resetForNewSession();nm.cancelAll()
        NetraApplication.instance.settingsRepository.setUnplugAlarmEnabled(true)
        NetraApplication.instance.settingsRepository.setThermalWarningThreshold(40f)
    }
    @Test fun heatCooldownSurvivesServiceRecreation() {
        val s=sample(46f);service().dispatchSafetyAndRecommendations(s) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_OVERHEAT_ID))
        nm.cancelAll();service().dispatchSafetyAndRecommendations(s) {}
        assertNull("Restart must not immediately repeat critical heat notification",shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_OVERHEAT_ID))
    }
    @Test fun thresholdCooldownSurvivesServiceRecreation() {
        val s=sample(42f);service().dispatchSafetyAndRecommendations(s) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_THRESHOLD_ID))
        nm.cancelAll();service().dispatchSafetyAndRecommendations(s) {}
        assertEquals("Restart must retain threshold cooldown",0,shadowOf(nm).size())
    }
    @Test fun criticalEscalationIsNotSuppressedByThresholdCooldown() {
        val s=service();val st=sample(42f);s.dispatchSafetyAndRecommendations(st) {};nm.cancelAll()
        service().dispatchSafetyAndRecommendations(st.copy(temperatureCelsius=46f)) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_OVERHEAT_ID))
    }
    @Test fun targetFirstAlertDoesNotReplayOnServiceRecreation() {
        val st=sample();service().dispatchSafetyAndRecommendations(st) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
        nm.cancelAll();service().dispatchSafetyAndRecommendations(st) {}
        assertNull("Target first alert must not replay",shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }
    @Test fun dismissedTargetSurvivesProcessMemoryReset() {
        val st=sample();service().dispatchSafetyAndRecommendations(st) {}
        AlarmActionReceiver().onReceive(c,AlarmActionReceiver.intent(c,BatteryMonitorService.ACTION_DISMISS_ALARM))
        TargetAlarmRepeat.resetForNewSession() // simulate globals lost in a new process, not a charger event
        service().dispatchSafetyAndRecommendations(st) {}
        assertNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
        assertTrue("Restored session must retain Dismiss",TargetAlarmRepeat.muted)
    }

    @Test fun continueTo100SurvivesMemoryResetWithoutChangingSavedSetting() {
        val original = NetraApplication.instance.settingsRepository.settings.value.chargeTargetPercent
        val st=sample();service().dispatchSafetyAndRecommendations(st) {}
        AlarmActionReceiver().onReceive(c,AlarmActionReceiver.intent(c,BatteryMonitorService.ACTION_SET_TARGET_100))
        TargetAlarmRepeat.resetForNewSession();nm.cancelAll()
        service().dispatchSafetyAndRecommendations(st) {}
        assertEquals(100,TargetAlarmRepeat.sessionTarget)
        assertEquals(original, NetraApplication.instance.settingsRepository.settings.value.chargeTargetPercent)
        assertNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }
    @Test fun newCanonicalSessionRearmsDismissedTarget() {
        val st=sample();service().dispatchSafetyAndRecommendations(st) {}
        AlarmActionReceiver().onReceive(c,AlarmActionReceiver.intent(c,BatteryMonitorService.ACTION_DISMISS_ALARM))
        service().dispatchSafetyAndRecommendations(st.copy(chargerConnectedAt=st.chargerConnectedAt!!+1)) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
        assertFalse(TargetAlarmRepeat.muted)
    }
    @Test fun criticalCooldownDoesNotFallThroughToLowerSeverityAlert() {
        val st=sample(46f);val s=service();s.dispatchSafetyAndRecommendations(st) {};nm.cancelAll()
        s.dispatchSafetyAndRecommendations(st) {}
        assertEquals(0,shadowOf(nm).size())
    }
    @Test fun futurePersistedHeatTimestampCannotSuppressWarning() {
        c.getSharedPreferences("netra_alert_delivery_v1",Context.MODE_PRIVATE).edit().putLong("overheat_at",System.currentTimeMillis()+900_000).commit()
        service().dispatchSafetyAndRecommendations(sample(46f)) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_OVERHEAT_ID))
    }
    @Test fun heatAlertRepeatsAfterExistingInterval() {
        c.getSharedPreferences("netra_alert_delivery_v1",Context.MODE_PRIVATE).edit().putLong("overheat_at",System.currentTimeMillis()-121_000).commit()
        service().dispatchSafetyAndRecommendations(sample(46f)) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_OVERHEAT_ID))
    }

    @Test fun missingSessionIdentityDoesNotReplayEverySample() {
        val st=sample().copy(chargerConnectedAt=null);val s=service()
        s.dispatchSafetyAndRecommendations(st) {};nm.cancelAll()
        s.dispatchSafetyAndRecommendations(st) {}
        assertNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }
    @Test fun oldSessionNotificationActionCannotMuteNewSession() {
        val s=service();val st=sample();s.dispatchSafetyAndRecommendations(st) {}
        val old=AlarmActionReceiver.intent(c,BatteryMonitorService.ACTION_DISMISS_ALARM)
        s.dispatchSafetyAndRecommendations(st.copy(chargerConnectedAt=st.chargerConnectedAt!!+1)) {}
        AlarmActionReceiver().onReceive(c,old)
        assertFalse(TargetAlarmRepeat.muted)
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }
    @Test fun unboundOldNotificationActionCannotMuteCurrentSession() {
        service().dispatchSafetyAndRecommendations(sample()) {}
        AlarmActionReceiver().onReceive(c,AlarmActionReceiver.intent(c,BatteryMonitorService.ACTION_DISMISS_ALARM,null))
        assertFalse(TargetAlarmRepeat.muted)
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }
    @Test fun changedThresholdDoesNotInheritOldThresholdCooldown() {
        val s=sample(42f);service().dispatchSafetyAndRecommendations(s) {};nm.cancelAll()
        NetraApplication.instance.settingsRepository.setThermalWarningThreshold(41f)
        service().dispatchSafetyAndRecommendations(s) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_THRESHOLD_ID))
    }
    @Test fun targetRepeatsAfterExistingTwoMinuteInterval() {
        val st=sample().copy(chargerConnectedAt=System.currentTimeMillis()-300_000)
        c.getSharedPreferences("netra_alert_delivery_v1",Context.MODE_PRIVATE).edit()
            .putLong("session",st.chargerConnectedAt!!).putInt("target",80)
            .putLong("target_at",System.currentTimeMillis()-121_000).commit()
        service().dispatchSafetyAndRecommendations(st) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }
    @Test fun disconnectedSampleClearsPersistedDismiss() {
        val s=service();val st=sample();s.dispatchSafetyAndRecommendations(st) {}
        AlarmActionReceiver().onReceive(c,AlarmActionReceiver.intent(c,BatteryMonitorService.ACTION_DISMISS_ALARM))
        s.dispatchSafetyAndRecommendations(st.copy(isCharging=false,isChargerConnected=false,chargerConnectedAt=null)) {}
        assertFalse(c.getSharedPreferences("netra_alert_delivery_v1",Context.MODE_PRIVATE).contains("session"))
        assertFalse(TargetAlarmRepeat.muted)
    }

    @Test fun unavailableStorageRetainsLiveTargetAndHeatCooldowns() {
        val unavailable=org.mockito.Mockito.mock(Context::class.java)
        org.mockito.Mockito.`when`(unavailable.applicationContext).thenReturn(unavailable)
        org.mockito.Mockito.`when`(unavailable.getSharedPreferences(org.mockito.Mockito.anyString(),org.mockito.Mockito.anyInt()))
            .thenThrow(IllegalStateException("unavailable"))
        val s=service()
        BatteryMonitorService::class.java.getDeclaredField("alertDelivery\$delegate").apply { isAccessible=true }
            .set(s,lazy { com.example.service.AlertDeliveryState(unavailable) })
        val st=sample(46f);s.dispatchSafetyAndRecommendations(st) {};nm.cancelAll()
        s.dispatchSafetyAndRecommendations(st) {}
        assertEquals(0,shadowOf(nm).size())
        AlarmActionReceiver().onReceive(c,AlarmActionReceiver.intent(c,BatteryMonitorService.ACTION_DISMISS_ALARM))
        s.dispatchSafetyAndRecommendations(st) {}
        assertTrue(TargetAlarmRepeat.muted)
    }
    @Test fun futureLiveTargetTimestampCannotSuppressWarning() {
        val s=service();val st=sample();s.dispatchSafetyAndRecommendations(st) {};nm.cancelAll()
        BatteryMonitorService::class.java.getDeclaredField("lastTargetAlarmAt").apply { isAccessible=true }
            .setLong(s,System.currentTimeMillis()+900_000)
        s.dispatchSafetyAndRecommendations(st) {}
        assertNotNull(shadowOf(nm).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }

}
