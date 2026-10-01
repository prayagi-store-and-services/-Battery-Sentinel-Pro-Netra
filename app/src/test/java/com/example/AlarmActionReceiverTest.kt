package com.example

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepository
import com.example.receiver.AlarmActionReceiver
import com.example.service.BatteryMonitorService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AlarmActionReceiverTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        context.getSharedPreferences("netra_sentinel_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `both alarm intents have app package and exact nonexported receiver`() {
        for (action in listOf(BatteryMonitorService.ACTION_DISMISS_ALARM, BatteryMonitorService.ACTION_SET_TARGET_100)) {
            val intent = AlarmActionReceiver.intent(context, action)
            assertEquals(context.packageName, intent.`package`)
            assertEquals(AlarmActionReceiver::class.java.name, intent.component!!.className)
            assertEquals(action, intent.action)
            val info = context.packageManager.getReceiverInfo(intent.component!!, 0)
            assertFalse(info.exported)
            val pending = PendingIntent.getBroadcast(context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            assertTrue(pending.isImmutable)
        }
    }

    private fun postAlarm() {
        manager.notify(BatteryMonitorService.NOTIFICATION_ALARM_ID,
            Notification.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info).build())
    }

    @Test fun `dismiss cancels alarm without changing target`() {
        postAlarm()
        AlarmActionReceiver().onReceive(context, AlarmActionReceiver.intent(context, BatteryMonitorService.ACTION_DISMISS_ALARM))
        assertNull(shadowOf(manager).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
        assertEquals(80, SettingsRepository(context).settings.value.chargeTargetPercent)
    }

    @Test fun `continue charging persists 100 target and cancels alarm`() {
        postAlarm()
        AlarmActionReceiver().onReceive(context, AlarmActionReceiver.intent(context, BatteryMonitorService.ACTION_SET_TARGET_100))
        assertEquals(100, SettingsRepository(context).settings.value.chargeTargetPercent)
        assertNull(shadowOf(manager).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }

    @Test fun `unknown action cannot change settings or cancel alarm`() {
        postAlarm()
        AlarmActionReceiver().onReceive(context, Intent("untrusted.action"))
        assertEquals(80, SettingsRepository(context).settings.value.chargeTargetPercent)
        assertNotNull(shadowOf(manager).getNotification(BatteryMonitorService.NOTIFICATION_ALARM_ID))
    }
}
