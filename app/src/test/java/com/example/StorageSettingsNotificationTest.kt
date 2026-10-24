package com.example

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.service.JunkWatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = NetraApplication::class)
class StorageSettingsNotificationTest {
    @Test fun notificationHasFixedExplicitImmutableDestination() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val pending = JunkWatcher.storageSettingsPendingIntent(ctx)
        val intent = shadowOf(pending).savedIntent
        assertEquals(android.content.ComponentName(ctx, StorageSettingsActivity::class.java), intent.component)
        assertEquals(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, intent.action)
        assertTrue(pending.isImmutable)
    }

    @Test fun storageNotificationEntryPointIsNotExported() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val info = ctx.packageManager.getActivityInfo(
            android.content.ComponentName(ctx, StorageSettingsActivity::class.java), 0)
        assertFalse(info.exported)
    }

    @Test fun entryPointIgnoresCallerSuppliedIntentAndFinishes() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val hostile = Intent(ctx, StorageSettingsActivity::class.java)
            .putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.invalid/")))
        val activity = Robolectric.buildActivity(StorageSettingsActivity::class.java, hostile).create().get()
        assertTrue(activity.isFinishing)
        val launched = shadowOf(activity).nextStartedActivity
        if (launched != null) {
            assertNotNull(launched.component)
            assertNotEquals(Intent.ACTION_VIEW, launched.action)
            assertNull(launched.data)
        }
    }

    @Test fun missingTrustedSettingsHandlerFailsSafely() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(JunkWatcher.openStorageSettings(ctx))
    }
}
