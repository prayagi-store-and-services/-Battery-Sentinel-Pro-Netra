package com.example

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerSettingsActivityTest {
    @Test fun fixedSettingsDestinationsDropUntrustedPayload() {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (action in listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS)) {
            shadowOf(c.packageManager).addResolveInfoForIntent(Intent(action), ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = "com.android.settings"; name = "Settings"; exported = true }
            })
            val input = Intent(c, PowerSettingsActivity::class.java).setAction(action)
                .setData(android.net.Uri.parse("content://untrusted/payload"))
                .putExtra("untrusted", "ignored").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val a = Robolectric.buildActivity(PowerSettingsActivity::class.java, input).create().get()
            val opened = shadowOf(a).nextStartedActivity
            assertEquals(action, opened.action)
            assertNull(opened.data)
            assertNull(opened.extras)
            assertEquals(0, opened.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertTrue(a.isFinishing)
        }
        val info = c.packageManager.getActivityInfo(android.content.ComponentName(c, PowerSettingsActivity::class.java), 0)
        assertFalse(info.exported)
    }
    @Test fun unknownActionDoesNothing() {
        val a = Robolectric.buildActivity(PowerSettingsActivity::class.java, Intent().setAction(Intent.ACTION_VIEW)).create().get()
        assertNull(shadowOf(a).nextStartedActivity)
        assertTrue(a.isFinishing)
    }
    @Test fun unavailableSettingsAreHandled() {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(c.packageManager).removeResolveInfosForIntent(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), "com.android.settings")
        shadowOf(c as android.app.Application).checkActivities(true)
        val a = Robolectric.buildActivity(PowerSettingsActivity::class.java, Intent().setAction(Settings.ACTION_BATTERY_SAVER_SETTINGS)).create().get()
        assertTrue(a.isFinishing)
        assertTrue(ShadowToast.getTextOfLatestToast().toString().contains("unavailable"))
        shadowOf(c as android.app.Application).checkActivities(false)
    }
}
