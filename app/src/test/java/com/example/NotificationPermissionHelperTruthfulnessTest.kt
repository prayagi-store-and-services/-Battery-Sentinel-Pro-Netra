package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.PermissionHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationPermissionHelperTruthfulnessTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun notificationsEnabled(enabled: Boolean) {
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .setNotificationsEnabled(enabled)
    }
    private fun status() = PermissionHelper.isNotificationGranted(context)

    @Test fun deniedPermissionCannotReportGranted() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationsEnabled(true)
        assertEquals(false, status())
    }
    @Test fun disabledAppSwitchCannotReportGranted() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationsEnabled(false)
        assertEquals(false, status())
        assertEquals(false, PermissionHelper.checkAllPermissions(context).isNotificationGranted)
    }
    @Test fun grantedAndEnabledReportsGranted() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationsEnabled(true)
        assertEquals(true, status())
    }
    @Test fun laterSwitchDisableRevokesGranted() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationsEnabled(true)
        assertEquals(true, status())
        notificationsEnabled(false)
        assertEquals(false, status())
    }
    @Test @Config(sdk = [28]) fun olderAndroidStillUsesAppSwitch() {
        notificationsEnabled(false)
        assertEquals(false, status())
        notificationsEnabled(true)
        assertEquals(true, status())
    }
}
