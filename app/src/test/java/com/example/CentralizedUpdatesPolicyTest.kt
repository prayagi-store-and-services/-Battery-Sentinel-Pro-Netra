package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * App updates are centralized in Netra Ecosystem (user decision 2026-10-25): this app must not
 * carry an APK install permission, an updater component entry point, or any download/install
 * machinery of its own. These assertions replace the removed updater feature contracts; they
 * pin the new no-capability policy instead of pretending the old tests still apply.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CentralizedUpdatesPolicyTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun manifestDeclaresNoPackageInstallPermission() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(
            "REQUEST_INSTALL_PACKAGES must not be declared; updates come only from Netra Ecosystem",
            info.requestedPermissions?.contains("android.permission.REQUEST_INSTALL_PACKAGES") == true
        )
    }

    @Test fun manifestHasNoUpdaterComponents() {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val names = (info.activities?.map { it.name } ?: emptyList()) +
            (info.receivers?.map { it.name } ?: emptyList()) +
            (info.services?.map { it.name } ?: emptyList()) +
            (info.providers?.map { it.name } ?: emptyList())
        val leftover = names.filter { it.contains(".update.") || it.contains("InstallResultReceiver") }
        assertTrue("updater components must be gone, found: $leftover", leftover.isEmpty())
    }

    @Test fun updaterClassesAreGone() {
        for (cls in listOf(
            "com.example.update.GitHubReleaseUpdater",
            "com.example.update.UpdateCheckWorker",
            "com.example.update.InstallResultReceiver",
            "com.example.update.PreDownload"
        )) {
            try {
                Class.forName(cls)
                fail<Unit>("removed updater class still present: $cls")
            } catch (_: ClassNotFoundException) {
                // expected
            }
        }
    }

    @Test fun reportFileProviderSurvives() {
        // The FileProvider stays: it serves generated battery PDF/CSV reports, unrelated to updates.
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS)
        assertTrue(
            info.providers?.any { it.name == "androidx.core.content.FileProvider" } == true
        )
    }
}
