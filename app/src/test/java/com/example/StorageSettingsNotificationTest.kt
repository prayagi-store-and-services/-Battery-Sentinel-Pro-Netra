package com.example

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
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
        val expected = installHandler(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "trusted.settings", true)
        val hostile = Intent(ctx, StorageSettingsActivity::class.java)
            .putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.invalid/")))
        val activity = Robolectric.buildActivity(StorageSettingsActivity::class.java, hostile).create().get()
        assertTrue(activity.isFinishing)
        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull("Trusted storage handler must actually launch", launched)
        assertEquals(expected, launched.component)
        assertEquals(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, launched.action)
        assertNull(launched.data)
        assertFalse(launched.hasExtra(Intent.EXTRA_INTENT))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Suppress("DEPRECATION")
    private fun installHandler(ctx: Context, action: String, pkg: String, system: Boolean,
        exported: Boolean = true, enabled: Boolean = true): ComponentName {
        val component = ComponentName(pkg, "$pkg.StorageActivity")
        val info = ActivityInfo().apply {
            packageName = pkg
            name = component.className
            this.exported = exported
            this.enabled = enabled
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                flags = ApplicationInfo.FLAG_INSTALLED or (if (system) ApplicationInfo.FLAG_SYSTEM else 0)
                this.enabled = true
            }
        }
        shadowOf(ctx.packageManager).addResolveInfoForIntent(Intent(action), ResolveInfo().apply { activityInfo = info })
        return component
    }

    @Test fun thirdPartyHandlerIsRejectedAndTrustedFallbackOpens() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        installHandler(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "untrusted.handler", false)
        val expected = installHandler(ctx, Settings.ACTION_APPLICATION_SETTINGS, "trusted.fallback", true)
        assertTrue(JunkWatcher.openStorageSettings(ctx))
        val launched = shadowOf(ctx as android.app.Application).nextStartedActivity
        assertNotNull(launched)
        assertEquals(expected, launched.component)
        assertEquals(Settings.ACTION_APPLICATION_SETTINGS, launched.action)
    }

    @Test fun nonexportedAndDisabledSystemHandlersAreRejected() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        installHandler(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "private.system", true, exported = false)
        installHandler(ctx, Settings.ACTION_MEMORY_CARD_SETTINGS, "disabled.system", true, enabled = false)
        val expected = installHandler(ctx, Settings.ACTION_APPLICATION_SETTINGS, "trusted.final", true)
        assertTrue(JunkWatcher.openStorageSettings(ctx))
        val launched = shadowOf(ctx as android.app.Application).nextStartedActivity
        assertNotNull(launched)
        assertEquals(expected, launched.component)
    }

    @Test fun failedTrustedLaunchTriesNextTrustedAction() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        installHandler(base, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "trusted.blocked", true)
        val expected = installHandler(base, Settings.ACTION_MEMORY_CARD_SETTINGS, "trusted.recovery", true)
        val attempts = mutableListOf<Intent>()
        val wrapper = object : android.content.ContextWrapper(base) {
            override fun startActivity(intent: Intent) {
                attempts.add(intent)
                if (intent.action == Settings.ACTION_INTERNAL_STORAGE_SETTINGS) {
                    throw android.content.ActivityNotFoundException("test-only unavailable handler")
                }
            }
        }
        assertTrue(JunkWatcher.openStorageSettings(wrapper))
        assertEquals(2, attempts.size)
        assertEquals(expected, attempts.last().component)
        assertEquals(Settings.ACTION_MEMORY_CARD_SETTINGS, attempts.last().action)
    }

    @Test fun missingTrustedSettingsHandlerFailsSafely() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(JunkWatcher.openStorageSettings(ctx))
    }
}
