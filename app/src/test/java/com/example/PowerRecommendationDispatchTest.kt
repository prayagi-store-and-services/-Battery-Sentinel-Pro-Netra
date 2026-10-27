package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NetraCentralState
import com.example.service.PowerRecommendations as P
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerRecommendationDispatchTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun sample(level: Int, charging: Boolean = false) = NetraCentralState(
        batteryLevel = level, isCharging = charging, isChargerConnected = charging, isDataFresh = true)
    @Before fun reset() {
        context.getSharedPreferences("netra_power_recommendations_v1", Context.MODE_PRIVATE).edit().clear().commit()
        manager.cancelAll()
        NetraApplication.instance.settingsRepository.setNotificationEnabled(true)
    }
    @Test fun realDispatcherPostsSilentOnceAndResetsOnlyOnConnection() {
        P.onSample(context, sample(20))
        val n = shadowOf(manager).getNotification(P.ID)
        assertNotNull(n)
        assertEquals("Power-saving suggestion: 20%", n.extras.getString("android.title"))
        assertEquals(2, n.actions.size)
        listOf(n.contentIntent, n.actions[0].actionIntent, n.actions[1].actionIntent).forEach {
            val pending = shadowOf(it)
            assertEquals(android.content.ComponentName(context, PowerSettingsActivity::class.java), pending.savedIntent.component)
            assertTrue(pending.flags and android.app.PendingIntent.FLAG_IMMUTABLE != 0)
        }
        assertEquals(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS, shadowOf(n.actions[0].actionIntent).savedIntent.action)
        assertEquals(android.provider.Settings.ACTION_DISPLAY_SETTINGS, shadowOf(n.actions[1].actionIntent).savedIntent.action)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(P.CHANNEL).importance)
        assertNull(manager.getNotificationChannel(P.CHANNEL).sound)
        manager.cancel(P.ID)
        P.onSample(context, sample(19))
        assertNull(shadowOf(manager).getNotification(P.ID))
        P.onSample(context, sample(80, true))
        P.onSample(context, sample(20))
        assertNotNull(shadowOf(manager).getNotification(P.ID))
    }
    @Test fun persistedConsumptionSurvivesAndMasterOffDoesNotConsume() {
        NetraApplication.instance.settingsRepository.setNotificationEnabled(false)
        P.onSample(context, sample(10))
        assertNull(shadowOf(manager).getNotification(P.ID))
        NetraApplication.instance.settingsRepository.setNotificationEnabled(true)
        P.onSample(context, sample(10))
        assertNotNull(shadowOf(manager).getNotification(P.ID))
        val saved = context.getSharedPreferences("netra_power_recommendations_v1", Context.MODE_PRIVATE).getStringSet("seen", emptySet())!!
        assertTrue(saved.containsAll(setOf("50","30","20","10")))
        manager.cancel(P.ID)
        P.onSample(context, sample(11))
        P.onSample(context, sample(10))
        assertNull(shadowOf(manager).getNotification(P.ID))
    }
    @Test fun staleOrUnknownCannotResetOrPost() {
        P.onSample(context, sample(10))
        manager.cancel(P.ID)
        P.onSample(context, sample(80, true).copy(isDataFresh = false))
        P.onSample(context, sample(10))
        assertNull(shadowOf(manager).getNotification(P.ID))
        P.onSample(context, sample(5).copy(isChargerConnected = null))
        assertNull(shadowOf(manager).getNotification(P.ID))
    }
    @Test fun recommendationExceptionCannotSuppressRealCriticalNotification() {
        val service = org.robolectric.Robolectric.buildService(com.example.service.BatteryMonitorService::class.java).get()
        service.dispatchSafetyAndRecommendations(sample(10).copy(temperatureCelsius = 46f)) { throw IllegalStateException("injected") }
        assertNotNull(shadowOf(manager).getNotification(com.example.service.BatteryMonitorService.NOTIFICATION_OVERHEAT_ID))
    }
    @Test fun skippedAndDisabledLevelsUseOneActualPercentageTitle() {
        P.onSample(context, sample(2))
        assertEquals("Power-saving suggestion: 2%", shadowOf(manager).getNotification(P.ID).extras.getString("android.title"))
        assertEquals(1, shadowOf(manager).size())
        P.onSample(context, sample(80,true))
        assertNull(shadowOf(manager).getNotification(P.ID))
        P.setEnabled(context, 5, false)
        P.onSample(context, sample(5))
        assertEquals("Power-saving suggestion: 5%", shadowOf(manager).getNotification(P.ID).extras.getString("android.title"))
        P.onSample(context, sample(80,true))
        P.levels.forEach { P.setEnabled(context,it,false) }
        P.onSample(context,sample(2))
        assertNull(shadowOf(manager).getNotification(P.ID))
    }
    @Test fun missedChargeRearmsWithFivePointHysteresis() {
        P.onSample(context,sample(20));manager.cancel(P.ID)
        P.onSample(context,sample(24));P.onSample(context,sample(20))
        assertNull(shadowOf(manager).getNotification(P.ID))
        P.onSample(context,sample(26));manager.cancel(P.ID)
        P.onSample(context,sample(20))
        assertNotNull(shadowOf(manager).getNotification(P.ID))
    }
    @Test fun osOffAndBlockedChannelDoNotConsume() {
        shadowOf(manager).setNotificationsEnabled(false)
        P.onSample(context,sample(10))
        assertNull(shadowOf(manager).getNotification(P.ID))
        shadowOf(manager).setNotificationsEnabled(true)
        manager.createNotificationChannel(android.app.NotificationChannel(P.CHANNEL,"test",NotificationManager.IMPORTANCE_NONE))
        P.onSample(context,sample(10))
        assertNull(shadowOf(manager).getNotification(P.ID))
    }

}
