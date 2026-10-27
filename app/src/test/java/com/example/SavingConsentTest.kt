package com.example

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.service.SavingConsent
import com.example.service.SavingSettings
import com.example.service.SavingSystemIo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SavingConsentTest {
    private lateinit var c: Context
    @Before fun reset() {
        c = ApplicationProvider.getApplicationContext()
        SavingSettings.resetIo()
        listOf("netra_sentinel_prefs", "netra_power_prefs", "netra_profile_applied").forEach {
            c.getSharedPreferences(it, 0).edit().clear().commit()
        }
        c.getSharedPreferences("netra_saving_consent_v1", 0).edit().clear().commit()
        c.getSharedPreferences("netra_saving_restore_v1", 0).edit().clear().commit()
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 190)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000)
        ContentResolver.setMasterSyncAutomatically(true)
    }
    @Test fun defaultsNeverChangeDisplayOrSync() {
        assertFalse(SavingConsent.displayAllowed(c))
        assertFalse(SavingConsent.syncAllowed(c))
        assertFalse(SavingSettings.display(c, "test", 10, 15000))
        assertFalse(SavingSettings.pauseSync(c, "test"))
        assertEquals(190, Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        assertTrue(ContentResolver.getMasterSyncAutomatically())
    }
    @Test fun syncRequiresSeparateConsentAndRestoresExactly() {
        SavingConsent.setDisplay(c, true)
        assertFalse(SavingSettings.pauseSync(c, "test"))
        SavingConsent.setSync(c, true)
        assertTrue(SavingSettings.pauseSync(c, "test"))
        assertFalse(ContentResolver.getMasterSyncAutomatically())
        assertFalse(SavingSettings.pauseSync(c, "other"))
        assertFalse(SavingSettings.restoreSync(c, "other"))
        SavingConsent.setSync(c, false)
        assertTrue(ContentResolver.getMasterSyncAutomatically())
    }
    @Test fun syncInitiallyOffStaysOffAfterUndo() {
        ContentResolver.setMasterSyncAutomatically(false)
        SavingConsent.setSync(c, true)
        assertTrue(SavingSettings.pauseSync(c, "test"))
        SavingSettings.undo(c)
        assertFalse(ContentResolver.getMasterSyncAutomatically())
    }
    @Test fun displayRestorePointIsNotOverwrittenByOtherSaver() {
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "test", 10, 15000))
        assertFalse(SavingSettings.display(c, "other", 30, 30000))
        assertFalse(SavingSettings.restoreDisplay(c, "other"))
        assertTrue(SavingSettings.restoreDisplay(c, "test"))
        assertEquals(190, Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(0, Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE))
        assertEquals(60000, Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT))
    }
    @Test fun laterManualDisplayChangeIsKept() {
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "test", 10, 15000))
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 210)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 45000)
        SavingConsent.setDisplay(c, false)
        assertEquals(210, Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(45000, Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT))
    }

    private class FakeIo : SavingSystemIo {
        val values = mutableMapOf(Settings.System.SCREEN_BRIGHTNESS to 190, Settings.System.SCREEN_BRIGHTNESS_MODE to 1, Settings.System.SCREEN_OFF_TIMEOUT to 60000)
        var permission = true
        var syncOn = true
        var fail: ((String, Int) -> Boolean)? = null
        override fun canWrite(c: Context) = permission
        override fun read(c: Context, key: String) = values.getValue(key)
        override fun write(c: Context, key: String, value: Int): Boolean {
            if (fail?.invoke(key, value) == true) return false
            values[key] = value
            return true
        }
        override fun sync() = syncOn
        override fun sync(value: Boolean) { syncOn = value }
    }
    @Test fun modeWriteSucceedsBrightnessFailsAndRollbackRestoresMode() {
        val io = FakeIo(); SavingSettings.io = io
        io.fail = { key, value -> key == Settings.System.SCREEN_BRIGHTNESS && value == 10 }
        SavingConsent.setDisplay(c, true)
        assertFalse(SavingSettings.display(c, "test", 10))
        assertEquals(1, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }
    @Test fun brightnessRestoreSuccessCannotDiscardFailedModeRestore() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "test", 10, 15000))
        io.fail = { key, value -> key == Settings.System.SCREEN_BRIGHTNESS_MODE && value == 1 }
        assertFalse(SavingSettings.restoreDisplay(c, "test"))
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertEquals(0, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
        assertTrue(SavingSettings.status(c).contains("Restore failed"))
        io.fail = null
        assertTrue(SavingSettings.restoreDisplay(c, "test"))
        assertEquals(1, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
    }
    @Test fun sameOwnerNullTimeoutKeepsExistingRestoreTracking() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "test", 10, 15000))
        assertTrue(SavingSettings.display(c, "test", 20, null))
        assertTrue(SavingSettings.restoreDisplay(c, "test"))
        assertEquals(60000, io.values[Settings.System.SCREEN_OFF_TIMEOUT])
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }
    @Test fun externalEditStopsOwnerReapplyAndKeepsTheEditedValue() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "test", 10, 15000))
        io.values[Settings.System.SCREEN_BRIGHTNESS] = 210
        assertFalse(SavingSettings.display(c, "test", 20, null))
        assertEquals(210, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertTrue(SavingSettings.restoreDisplay(c, "test"))
        assertEquals(210, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertEquals(60000, io.values[Settings.System.SCREEN_OFF_TIMEOUT])
    }
    @Test fun deniedRestoreRetainsRecordsForPermissionReturnRetry() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "test", 10, 15000))
        io.permission = false
        assertFalse(SavingSettings.restoreDisplay(c, "test"))
        assertTrue(SavingSettings.status(c).contains("Restore pending"))
        io.permission = true
        assertTrue(SavingSettings.restoreDisplay(c, "test"))
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }
    @Test fun concurrentOppositeConsentTogglesRemainConsistent() {
        val io = FakeIo(); SavingSettings.io = io
        val start = java.util.concurrent.CountDownLatch(1)
        val a = Thread { start.await(); repeat(30) { SavingConsent.setDisplay(c, true); SavingSettings.display(c, "test", 10) } }
        val b = Thread { start.await(); repeat(30) { SavingConsent.setDisplay(c, false) } }
        a.start(); b.start(); start.countDown(); a.join(); b.join()
        SavingConsent.setDisplay(c, false)
        assertFalse(SavingConsent.displayAllowed(c))
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertEquals(1, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
    }

    @Test fun legacyManualRestoreWaitsForPermissionAndRestoresSavedModeExactly() {
        val io = FakeIo(); SavingSettings.io = io
        io.values[Settings.System.SCREEN_BRIGHTNESS] = 40
        io.values[Settings.System.SCREEN_BRIGHTNESS_MODE] = 0
        val old = c.getSharedPreferences("netra_power_prefs", 0)
        old.edit().putBoolean("bright_on", true).putInt("prev_brightness", 201).putInt("prev_mode", 1).commit()
        io.permission = false
        assertFalse(SavingSettings.migrateLegacy(c))
        assertTrue(old.contains("prev_brightness"))
        io.permission = true
        assertTrue(SavingSettings.migrateLegacy(c))
        assertEquals(201, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertEquals(1, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
        assertTrue(old.getBoolean("q2_done_prev_brightness", false))
    }
    @Test fun legacyActiveRestoresKnownTimeoutAndSyncWithoutInventingBrightness() {
        val io = FakeIo(); SavingSettings.io = io
        io.values[Settings.System.SCREEN_BRIGHTNESS] = 10
        io.values[Settings.System.SCREEN_OFF_TIMEOUT] = 15000
        io.syncOn = false
        c.getSharedPreferences("netra_sentinel_prefs", 0).edit().putBoolean("active_saver_applied", true)
            .putInt("active_saver_prev_timeout", 45000).putBoolean("active_saver_prev_sync", true).commit()
        assertTrue(SavingSettings.migrateLegacy(c))
        assertEquals(45000, io.values[Settings.System.SCREEN_OFF_TIMEOUT])
        assertTrue(io.syncOn)
        assertEquals(10, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertTrue(SavingSettings.status(c).contains("Exact recovery is unavailable"))
    }

    @Test fun inactiveLegacyKeysNeverEnableSyncOrChangeDisplay() {
        val io = FakeIo(); SavingSettings.io = io
        io.syncOn = false
        c.getSharedPreferences("netra_sentinel_prefs", 0).edit().putBoolean("active_saver_applied", false)
            .putInt("active_saver_prev_timeout", 45000).putBoolean("active_saver_prev_sync", true).commit()
        assertTrue(SavingSettings.migrateLegacy(c))
        assertFalse(io.syncOn)
        assertEquals(60000, io.values[Settings.System.SCREEN_OFF_TIMEOUT])
    }
    @Test fun ambiguousLegacyOverlapMakesNoWritesUntilExplicitCurrentChoice() {
        val io = FakeIo(); SavingSettings.io = io
        io.syncOn = false
        io.values[Settings.System.SCREEN_BRIGHTNESS] = 10
        val p = c.getSharedPreferences("netra_sentinel_prefs", 0)
        p.edit().putBoolean("active_saver_applied", true).putInt("active_saver_prev_timeout", 45000)
            .putBoolean("screen_off_saver_applied", true).putInt("screen_off_saver_prev_brightness", 40).commit()
        assertFalse(SavingSettings.migrateLegacy(c))
        assertEquals(10, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertTrue(p.contains("active_saver_prev_timeout"))
        SavingSettings.acceptLegacyCurrent(c)
        assertTrue(SavingSettings.migrateLegacy(c))
        assertFalse(io.syncOn)
    }
    @Test fun failedApplyPermissionLossSameOwnerRetryPreservesModeRestore() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        io.fail = { key, value ->
            if (key == Settings.System.SCREEN_BRIGHTNESS && value == 10) { io.permission = false; true }
            else !io.permission
        }
        assertFalse(SavingSettings.display(c, "test", 10))
        assertEquals(0, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
        io.permission = true; io.fail = null
        assertFalse(SavingSettings.display(c, "test", 10))
        assertEquals(1, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }

    @Test fun upgradedManualExternalEditKeepsBrightnessAndModeTogether() {
        val io = FakeIo(); SavingSettings.io = io
        io.values[Settings.System.SCREEN_BRIGHTNESS] = 211
        io.values[Settings.System.SCREEN_BRIGHTNESS_MODE] = 0
        c.getSharedPreferences("netra_power_prefs", 0).edit().putBoolean("bright_on", true)
            .putInt("prev_brightness", 190).putInt("prev_mode", 1).commit()
        assertTrue(SavingSettings.migrateLegacy(c))
        assertEquals(211, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertEquals(0, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
    }
    @Test fun retryDoesNotEndHealthyActiveDisplayOwnership() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "emergency", 64, 15000))
        SavingSettings.retryPending(c)
        assertEquals(64, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertFalse(SavingSettings.display(c, "other", 10))
    }
    @Test fun everyOwnerCanRetryRestorationAfterPermissionReturnWithoutBeingActive() {
        val io = FakeIo(); SavingSettings.io = io
        for (owner in listOf("profile", "active", "charging_screen_off", "saver", "thermal", "manual", "emergency")) {
            SavingConsent.setDisplay(c, true)
            assertTrue(SavingSettings.display(c, owner, 10, 15000))
            io.permission = false
            SavingSettings.restore(c, owner)
            assertTrue(c.getSharedPreferences("netra_saving_restore_v1", 0).contains("display_owner"))
            // Nothing in the recovery route relies on the original controller's in-memory applied flag.
            io.permission = true
            SavingSettings.retryPending(c)
            assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
            assertEquals(1, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
        }
    }

    @Test fun unresolvedProfileBlocksSyncUntilCurrentSettingsAcknowledged() {
        val io = FakeIo(); SavingSettings.io = io
        c.getSharedPreferences("netra_profile_applied", 0).edit().putBoolean("prev_sync", true).commit()
        SavingConsent.setSync(c, true)
        assertFalse(SavingSettings.pauseSync(c, "profile"))
        assertTrue(io.syncOn)
        SavingSettings.acceptLegacyCurrent(c)
        assertTrue(SavingSettings.pauseSync(c, "profile"))
    }
    @Test fun coldStartEndsHealthyOrphanFromEveryPreviousProcessSession() {
        val io = FakeIo(); SavingSettings.io = io
        for (owner in listOf("thermal", "profile", "active", "charging_screen_off", "saver", "manual", "emergency")) {
            SavingConsent.setDisplay(c, true); SavingConsent.setSync(c, true)
            assertTrue(SavingSettings.display(c, owner, 10, 15000))
            assertTrue(SavingSettings.pauseSync(c, owner))
            // No controller restore call, no prior error: launch only sees a persisted healthy ledger.
            SavingSettings.startNewProcessSessions(c)
            assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
            assertEquals(1, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
            assertEquals(60000, io.values[Settings.System.SCREEN_OFF_TIMEOUT])
            assertTrue(io.syncOn)
        }
    }
    @Test fun writeThenThrowRestoresActualChangedValueAndRetainsFailedMode() {
        val base = FakeIo()
        var fail = true
        SavingSettings.io = object : SavingSystemIo by base {
            override fun write(c: Context, key: String, value: Int): Boolean {
                if (fail && key == Settings.System.SCREEN_BRIGHTNESS && value == 10) {
                    base.values[key] = value
                    throw SecurityException("after write")
                }
                if (fail && key == Settings.System.SCREEN_BRIGHTNESS_MODE && value == 1) return false
                return base.write(c, key, value)
            }
        }
        SavingConsent.setDisplay(c, true)
        assertFalse(SavingSettings.display(c, "thermal", 10))
        assertEquals(190, base.values[Settings.System.SCREEN_BRIGHTNESS])
        assertEquals(0, base.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
        fail = false
        SavingSettings.startNewProcessSessions(c)
        assertEquals(1, base.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
    }
    @Test fun missingLegacyOriginalRequiresExplicitManualChoice() {
        val io = FakeIo(); SavingSettings.io = io
        c.getSharedPreferences("netra_power_prefs", 0).edit().putBoolean("bright_on", true).putInt("prev_brightness", 190).commit()
        assertFalse(SavingSettings.migrateLegacy(c))
        assertTrue(SavingSettings.legacyNeedsChoice(c))
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }

    @Test fun legacyPartialDisplayRecoverySurvivesColdStartForAllSavedOwners() {
        for (owner in listOf("manual", "screen", "saver")) {
            reset()
            val io = FakeIo(); SavingSettings.io = io
            io.values[Settings.System.SCREEN_BRIGHTNESS] = if (owner == "manual") 40 else 10
            io.values[Settings.System.SCREEN_BRIGHTNESS_MODE] = 0
            val p = c.getSharedPreferences(if (owner == "manual") "netra_power_prefs" else "netra_sentinel_prefs", 0)
            val flag = when(owner) { "manual" -> "bright_on"; "screen" -> "screen_off_saver_applied"; else -> "saver_display_changed" }
            val prefix = when(owner) { "manual" -> ""; "screen" -> "screen_off_saver_"; else -> "saver_" }
            p.edit().putBoolean(flag, true).putInt(prefix + "prev_brightness", 190).putInt(prefix + "prev_mode", 1)
                .putInt("saver_prev_timeout", 60000).commit()
            io.fail = { key, value -> key == Settings.System.SCREEN_BRIGHTNESS_MODE && value == 1 }
            assertFalse(SavingSettings.migrateLegacy(c))
            assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
            assertTrue(p.contains(prefix + "prev_mode"))
            io.fail = null
            SavingSettings.startNewProcessSessions(c)
            assertEquals(1, io.values[Settings.System.SCREEN_BRIGHTNESS_MODE])
            assertFalse(p.getBoolean(flag, false))
        }
    }
    @Test fun storageFailureAtStartupFailsClosedAndCanRetryWithoutCrashLoop() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "thermal", 10))
        SavingSettings.commitGate = { false }
        SavingSettings.startNewProcessSessions(c)
        assertTrue(SavingSettings.status(c).contains("storage failed"))
        assertFalse(SavingSettings.display(c, "thermal", 20))
        assertEquals(10, io.values[Settings.System.SCREEN_BRIGHTNESS])
        SavingSettings.commitGate = { true }
        SavingSettings.retryPending(c)
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }
    @Test fun allLegacyOriginalsMissingCannotSilentlyComplete() {
        val io = FakeIo(); SavingSettings.io = io
        c.getSharedPreferences("netra_sentinel_prefs", 0).edit().putBoolean("screen_off_saver_applied", true).commit()
        assertFalse(SavingSettings.migrateLegacy(c))
        assertTrue(SavingSettings.legacyNeedsChoice(c))
    }
    @Test fun coldStartPermissionDeniedThenGrantedRestoresBeforeProfileReapply() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "profile", 38, 15000))
        io.permission = false
        SavingSettings.startNewProcessSessions(c)
        assertEquals(38, io.values[Settings.System.SCREEN_BRIGHTNESS])
        io.permission = true
        SavingSettings.retryPending(c)
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
        assertTrue(SavingSettings.display(c, "profile", 102, 30000))
        SavingSettings.restore(c, "profile")
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }

    @Test fun repeatedSpecificEditorCommitFailureNeverRecursesAndCanRecoverLater() {
        val io = FakeIo(); SavingSettings.io = io
        SavingConsent.setDisplay(c, true)
        assertTrue(SavingSettings.display(c, "thermal", 10))
        var calls = 0
        SavingSettings.editorCommit = { calls++; false }
        SavingSettings.startNewProcessSessions(c)
        repeat(5) { SavingSettings.retryPending(c) }
        assertTrue(calls <= 6)
        assertTrue(SavingSettings.status(c).contains("storage failed"))
        assertEquals(10, io.values[Settings.System.SCREEN_BRIGHTNESS])
        SavingSettings.editorCommit = { it.commit() }
        SavingSettings.retryPending(c)
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }
    @Test fun legacyBrightnessModeDoneTimeoutFailureRetainsCompleteJournal() {
        val io = FakeIo(); SavingSettings.io = io
        io.values[Settings.System.SCREEN_BRIGHTNESS] = 10
        io.values[Settings.System.SCREEN_BRIGHTNESS_MODE] = 0
        io.values[Settings.System.SCREEN_OFF_TIMEOUT] = 15000
        val p = c.getSharedPreferences("netra_sentinel_prefs", 0)
        p.edit().putBoolean("saver_display_changed", true).putInt("saver_prev_brightness", 190)
            .putInt("saver_prev_mode", 1).putInt("saver_prev_timeout", 60000).commit()
        io.fail = { key, _ -> key == Settings.System.SCREEN_OFF_TIMEOUT }
        assertFalse(SavingSettings.migrateLegacy(c))
        assertTrue(p.getBoolean("q2_done_saver_prev_brightness", false))
        assertTrue(p.getBoolean("q2_done_saver_prev_mode", false))
        io.fail = null
        SavingSettings.startNewProcessSessions(c)
        assertFalse(p.getBoolean("saver_display_changed", false))
        assertEquals(60000, io.values[Settings.System.SCREEN_OFF_TIMEOUT])
    }
    @Test fun consentCommitFailureDoesNotAllowAnySettingMutation() {
        val io = FakeIo(); SavingSettings.io = io
        SavingSettings.editorCommit = { false }
        SavingConsent.setDisplay(c, true)
        assertFalse(SavingConsent.displayAllowed(c))
        assertFalse(SavingSettings.display(c, "test", 10))
        assertEquals(190, io.values[Settings.System.SCREEN_BRIGHTNESS])
    }

    @Test fun screenLegacyDoneDisplayFailedSyncRestartsOnlySync() {
        val base = FakeIo(); base.values[Settings.System.SCREEN_BRIGHTNESS] = 10; base.values[Settings.System.SCREEN_BRIGHTNESS_MODE] = 0; base.syncOn = false
        var failSync = true
        var displayWrites = 0
        SavingSettings.io = object : SavingSystemIo by base {
            override fun write(c: Context, key: String, value: Int): Boolean { displayWrites++; return base.write(c, key, value) }
            override fun sync(value: Boolean) { if (failSync) throw SecurityException("sync denied"); base.sync(value) }
        }
        val p = c.getSharedPreferences("netra_sentinel_prefs", 0)
        p.edit().putBoolean("screen_off_saver_applied", true).putInt("screen_off_saver_prev_brightness", 190)
            .putInt("screen_off_saver_prev_mode", 1).putBoolean("screen_off_saver_prev_sync", true).commit()
        assertFalse(SavingSettings.migrateLegacy(c))
        val firstWrites = displayWrites
        assertEquals(2, firstWrites)
        failSync = false
        SavingSettings.startNewProcessSessions(c)
        assertEquals(firstWrites, displayWrites)
        assertTrue(base.syncOn)
        assertFalse(p.getBoolean("screen_off_saver_applied", true))
    }
    @Test fun completionCommitFailureBeforeOrAfterMutationNeverRepeatsAndroidWrites() {
        for (afterMutation in listOf(false, true)) {
            reset()
            val base = FakeIo(); base.values[Settings.System.SCREEN_BRIGHTNESS] = 40; base.values[Settings.System.SCREEN_BRIGHTNESS_MODE] = 0
            var writes = 0
            SavingSettings.io = object : SavingSystemIo by base {
                override fun write(c: Context, key: String, value: Int): Boolean { writes++; return base.write(c, key, value) }
            }
            val p = c.getSharedPreferences("netra_power_prefs", 0)
            p.edit().putBoolean("bright_on", true).putInt("prev_brightness", 190).putInt("prev_mode", 1).commit()
            var calls = 0
            SavingSettings.editorCommit = {
                calls++
                if (calls == 7) { if (afterMutation) it.commit(); false } else it.commit()
            }
            SavingSettings.migrateLegacy(c)
            val first = writes
            SavingSettings.editorCommit = { it.commit() }
            SavingSettings.retryPending(c)
            assertEquals(first, writes)
            assertEquals(190, base.values[Settings.System.SCREEN_BRIGHTNESS])
        }
    }
    @Test fun throwingEditorIsCaughtAndBlocksWritesUntilRecovery() {
        val io = FakeIo(); SavingSettings.io = io
        SavingSettings.editorCommit = { throw IllegalStateException("disk unavailable") }
        SavingSettings.startNewProcessSessions(c)
        assertTrue(SavingSettings.status(c).contains("storage failed"))
        assertFalse(SavingSettings.display(c, "test", 10))
        SavingSettings.editorCommit = { it.commit() }
        SavingSettings.retryPending(c)
        assertFalse(SavingSettings.status(c).contains("storage failed"))
    }
}
