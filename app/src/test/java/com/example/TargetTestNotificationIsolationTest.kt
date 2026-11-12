package com.example

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.BatteryMonitorService
import com.example.service.BatteryNotificationIds
import com.example.service.TargetAlert
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TargetTestNotificationIsolationTest {
    @Test fun testNotificationLeavesLiveOverheatWarningIntact() {
        val c: Context = ApplicationProvider.getApplicationContext()
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(BatteryMonitorService.CHANNEL_ALERTS_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(BatteryNotificationIds.OVERHEAT, Notification.Builder(c, BatteryMonitorService.CHANNEL_ALERTS_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("Actual overheat").build())

        val result = TargetAlert.sendTest(c)

        assertTrue(result.startsWith("Test notification"))
        val posted = nm.activeNotifications.associateBy { it.id }
        assertEquals("Actual overheat", posted[BatteryNotificationIds.OVERHEAT]?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Test: charge target alert", posted[BatteryNotificationIds.TARGET_TEST]?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE).toString())
    }
}
