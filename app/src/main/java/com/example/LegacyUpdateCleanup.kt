package com.example

/**
 * One-time, idempotent removal of everything the deleted in-app updater could leave on a device
 * that ran an older version: its queued unique work, its status-bar notification and channel, its
 * cached installer files and its saved release metadata. App updates now come only from Netra
 * Ecosystem; nothing here re-registers any updater capability.
 *
 * Every step is isolated: a failure in one (for example WorkManager not being initialized in a
 * constrained environment) never skips the remaining steps. The seams keep this directly unit
 * testable without a device.
 */
internal object LegacyUpdateCleanup {
    const val WORK_NAME = "netra_update_check"
    const val CHANNEL_ID = "netra_app_update"
    const val NOTIFICATION_ID = 7430
    const val CACHE_DIR_NAME = "updates"
    const val PREFS_NAME = "netra_release_update_cache"

    interface NotificationOps {
        fun cancel(id: Int)
        fun deleteChannel(id: String)
    }

    interface WorkOps {
        fun cancelUniqueWork(name: String)
    }

    fun purge(
        cacheDir: java.io.File,
        prefs: android.content.SharedPreferences,
        notifications: NotificationOps,
        work: WorkOps,
        deleteChannelSupported: Boolean
    ) {
        try { work.cancelUniqueWork(WORK_NAME) } catch (_: Exception) {}
        try {
            if (deleteChannelSupported) notifications.deleteChannel(CHANNEL_ID)
            notifications.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        try { java.io.File(cacheDir, CACHE_DIR_NAME).listFiles()?.forEach { it.delete() } } catch (_: Exception) {}
        try { prefs.edit().clear().apply() } catch (_: Exception) {}
    }
}
