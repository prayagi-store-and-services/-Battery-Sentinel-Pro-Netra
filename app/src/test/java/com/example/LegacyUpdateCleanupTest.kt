package com.example

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Direct migration test for the removed updater's leftovers (no grep gates). */
class LegacyUpdateCleanupTest {

    private class FakeNotifications : LegacyUpdateCleanup.NotificationOps {
        val cancelled = mutableListOf<Int>()
        val deletedChannels = mutableListOf<String>()
        override fun cancel(id: Int) { cancelled.add(id) }
        override fun deleteChannel(id: String) { deletedChannels.add(id) }
    }

    private class FakeWork(private val fail: Boolean = false) : LegacyUpdateCleanup.WorkOps {
        val cancelledNames = mutableListOf<String>()
        override fun cancelUniqueWork(name: String) {
            if (fail) throw IllegalStateException("WorkManager not initialized")
            cancelledNames.add(name)
        }
    }

    private class FakeEditor : SharedPreferences.Editor {
        var cleared = false
        override fun clear(): SharedPreferences.Editor { cleared = true; return this }
        override fun apply() {}
        override fun commit() = true
        override fun putString(k: String?, v: String?) = this
        override fun putStringSet(k: String?, v: MutableSet<String>?) = this
        override fun putInt(k: String?, v: Int) = this
        override fun putLong(k: String?, v: Long) = this
        override fun putFloat(k: String?, v: Float) = this
        override fun putBoolean(k: String?, v: Boolean) = this
        override fun remove(k: String?) = this
    }

    private class FakePrefs(val editorImpl: FakeEditor = FakeEditor()) : SharedPreferences {
        override fun edit(): SharedPreferences.Editor = editorImpl
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
        override fun getString(k: String?, d: String?) = d
        override fun getStringSet(k: String?, d: MutableSet<String>?) = d
        override fun getInt(k: String?, d: Int) = d
        override fun getLong(k: String?, d: Long) = d
        override fun getFloat(k: String?, d: Float) = d
        override fun getBoolean(k: String?, d: Boolean) = d
        override fun contains(k: String?) = false
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun purgeRemovesEveryUpdaterLeftover() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val updates = java.io.File(dir, LegacyUpdateCleanup.CACHE_DIR_NAME).apply { mkdirs() }
        val staleApk = java.io.File(updates, "pending.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val prefs = FakePrefs()
        val notifications = FakeNotifications()
        val work = FakeWork()

        LegacyUpdateCleanup.purge(dir, prefs, notifications, work, deleteChannelSupported = true)

        assertEquals(listOf("netra_update_check"), work.cancelledNames)
        assertEquals(listOf("netra_app_update"), notifications.deletedChannels)
        assertEquals(listOf(7430), notifications.cancelled)
        assertFalse(staleApk.exists())
        assertTrue(prefs.editorImpl.cleared)
    }

    @Test
    fun workManagerFailureDoesNotSkipOtherSteps() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val updates = java.io.File(dir, LegacyUpdateCleanup.CACHE_DIR_NAME).apply { mkdirs() }
        val f = java.io.File(updates, "x.bin").apply { writeBytes(byteArrayOf(9)) }
        val prefs = FakePrefs()
        val notifications = FakeNotifications()

        LegacyUpdateCleanup.purge(dir, prefs, notifications, FakeWork(fail = true), deleteChannelSupported = false)

        assertEquals(emptyList<String>(), notifications.deletedChannels)
        assertEquals(listOf(7430), notifications.cancelled)
        assertFalse(f.exists())
        assertTrue(prefs.editorImpl.cleared)
    }

    @Test
    fun purgeIsIdempotentOnCleanState() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        LegacyUpdateCleanup.purge(dir, FakePrefs(), FakeNotifications(), FakeWork(), deleteChannelSupported = true)
        LegacyUpdateCleanup.purge(dir, FakePrefs(), FakeNotifications(), FakeWork(), deleteChannelSupported = true)
    }
}
