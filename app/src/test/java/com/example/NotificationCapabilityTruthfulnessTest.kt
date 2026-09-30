package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.service.CentralCapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationCapabilityTruthfulnessTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun notificationsEnabled(enabled: Boolean) {
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .setNotificationsEnabled(enabled)
    }
    private fun status() = CentralCapabilityRegistry(context).detectAllCapabilities()[CapabilityType.NOTIFICATIONS]

    @Test fun deniedPermissionRequiresPermissionEvenIfAppSwitchEnabled() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationsEnabled(true)
        assertEquals(CapabilityStatus.PERMISSION_REQUIRED, status())
    }
    @Test fun grantedPermissionWithDisabledAppNotificationsIsDisabled() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationsEnabled(false)
        assertEquals(CapabilityStatus.DISABLED, status())
    }
    @Test fun grantedAndEnabledIsAvailable() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationsEnabled(true)
        assertEquals(CapabilityStatus.AVAILABLE, status())
    }
    @Test fun notificationSwitchChangeRevokesAvailability() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationsEnabled(true)
        val registry = CentralCapabilityRegistry(context)
        assertEquals(CapabilityStatus.AVAILABLE, registry.detectAllCapabilities()[CapabilityType.NOTIFICATIONS])
        notificationsEnabled(false)
        assertEquals(CapabilityStatus.DISABLED, registry.detectAllCapabilities()[CapabilityType.NOTIFICATIONS])
    }
    @Test @Config(sdk = [28]) fun preRuntimePermissionUsesNotificationSwitch() {
        notificationsEnabled(false)
        assertEquals(CapabilityStatus.DISABLED, status())
        notificationsEnabled(true)
        assertEquals(CapabilityStatus.AVAILABLE, status())
    }
}
