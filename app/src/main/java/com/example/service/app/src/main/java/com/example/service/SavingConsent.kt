package com.example.service

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings

/** Separate opt-ins, never inferred from an old system permission or saver preference. */
object SavingConsent {
    private const val PREFS = "netra_saving_consent_v1"
    fun displayAllowed(c: Context) = prefs(c).getBoolean("display", false)
    fun syncAllowed(c: Context) = prefs(c).getBoolean("sync", false)
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun setDisplay(c: Context, allowed: Boolean) = synchronized(SavingSettings) {
        if (!SavingSettings.storeConsent(prefs(c).edit().putBoolean("display", allowed))) return@synchronized
        if (!allowed) runCatching { SavingSettings.restoreDisplay(c, null) }
        Unit
    }
    fun setSync(c: Context, allowed: Boolean) = synchronized(SavingSettings) {
        if (!SavingSettings.storeConsent(prefs(c).edit().putBoolean("sync", allowed))) return@synchronized
        if (!allowed) runCatching { SavingSettings.restoreSync(c, null) }
        Unit
    }
}

/** Injectable I/O boundary: failure tests exercise the same owner/restore logic as production. */
interface SavingSystemIo {
    fun canWrite(c: Context): Boolean
    fun read(c: Context, key: String): Int
    fun write(c: Context, key: String, value: Int): Boolean
    fun sync(): Boolean
    fun sync(value: Boolean)
}

/** Persistent write-ahead records, one owner, and independent recovery for each changed field. */
object SavingSettings {
    private const val PREFS = "netra_saving_restore_v1"
    private const val B = Settings.System.SCREEN_BRIGHTNESS
    private const val M = Settings.System.SCREEN_BRIGHTNESS_MODE
    private const val T = Settings.System.SCREEN_OFF_TIMEOUT
    private val fields = listOf(B, M, T)
    private val androidIo = object : SavingSystemIo {
        override fun canWrite(c: Context) = Settings.System.canWrite(c)
        override fun read(c: Context, key: String) = Settings.System.getInt(c.contentResolver, key)
        override fun write(c: Context, key: String, value: Int) = Settings.System.putInt(c.contentResolver, key, value)
        override fun sync() = ContentResolver.getMasterSyncAutomatically()
        override fun sync(value: Boolean) = ContentResolver.setMasterSyncAutomatically(value)
    }
    internal var io: SavingSystemIo = androidIo
    internal var commitGate: () -> Boolean = { true }
    internal var editorCommit: (android.content.SharedPreferences.Editor) -> Boolean = { it.commit() }
    private fun android.content.SharedPreferences.Editor.checkedCommit(): Boolean {
        val ok = try { commitGate() && editorCommit(this) } catch (e: Exception) { persistenceFailed = true; throw e }
        if (!ok) { persistenceFailed = true; throw IllegalStateException("Settings storage unavailable") }
        return true
    }
    internal fun storeConsent(edit: android.content.SharedPreferences.Editor): Boolean = runCatching { edit.checkedCommit() }.getOrDefault(false)
    private fun save(edit: android.content.SharedPreferences.Editor): Boolean = edit.checkedCommit()
    private var persistenceFailed = false
    internal fun resetIo() { io = androidIo; commitGate = { true }; editorCommit = { it.commit() }; persistenceFailed = false }
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun pending(c: Context, key: String) = prefs(c).contains("before_$key")
    private fun forget(c: Context, key: String) = prefs(c).edit().remove("before_$key").remove("applied_$key").checkedCommit()

    /** External edits release the changed fields and prevent that owner reapplying until it ends. */
    private fun reconcile(c: Context): Boolean {
        val p = prefs(c)
        var edited = false
        for (key in fields) if (pending(c, key) && io.read(c, key) != p.getInt("applied_$key", -1)) {
            forget(c, key)
            if (key == B) forget(c, M) // changing brightness also relinquishes adaptive-mode ownership
            edited = true
        }
        if (edited) p.edit().putBoolean("display_external", true).checkedCommit()
        return edited || p.getBoolean("display_external", false)
    }

    /** Finish old-version changes before accepting any new baseline. Never invent a missing original. */
    @Synchronized fun migrateLegacy(c: Context): Boolean = try {
        if (!commitGate()) { persistenceFailed = true; false } else migrateLegacyImpl(c)
    } catch (_: Exception) { persistenceFailed = true; false }
    private fun migrateLegacyImpl(c: Context): Boolean {
        val ledger = prefs(c)
        if (ledger.getBoolean("legacy_complete", false)) return true
        if (ledger.getBoolean("legacy_unknown", false)) return false
        val shared = c.getSharedPreferences("netra_sentinel_prefs", 0)
        val profile = c.getSharedPreferences("netra_profile_applied", 0)
        val manual = c.getSharedPreferences("netra_power_prefs", 0)
        var ok = true
        fun restore(p: android.content.SharedPreferences, old: String, field: String, applied: Set<Int>) {
            if (!p.contains(old) || p.getBoolean("q2_done_$old", false)) return
            val done = runCatching {
                check(io.canWrite(c))
                val actual = io.read(c, field)
                val before = p.getInt(old, -1)
                if (before < 0) {
                    check(ledger.edit().putBoolean("legacy_unknown", true).putString("legacy_error", "Unknown original in older-version records. Check Android settings, then Keep current settings; no baseline was invented.").checkedCommit())
                    error("unknown original")
                }
                if (actual in applied) { check(io.write(c, field, before)); check(io.read(c, field) == before) }
                check(p.edit().putBoolean("q2_done_$old", true).checkedCommit())
            }.isSuccess
            if (persistenceFailed) error("Settings storage unavailable")
            if (!done) ok = false
        }
        fun sync(p: android.content.SharedPreferences, old: String) {
            if (!p.contains(old) || p.getBoolean("q2_done_$old", false)) return
            val done = runCatching {
                if (!io.sync()) { io.sync(p.getBoolean(old, true)); check(io.sync() == p.getBoolean(old, true)) }
                check(p.edit().putBoolean("q2_done_$old", true).checkedCommit())
            }.isSuccess
            if (persistenceFailed) error("Settings storage unavailable")
            if (!done) ok = false
        }
        val active = shared.getBoolean("active_saver_applied", false)
        val screen = shared.getBoolean("screen_off_saver_applied", false)
        val manualOn = manual.getBoolean("bright_on", false)
        val saver = shared.getBoolean("saver_display_changed", false)
        val unknownProfile = profile.contains("prev_timeout") || profile.contains("prev_sync") || profile.getBoolean("dimmed", false)
        fun validPair(p: android.content.SharedPreferences, b: String, m: String): Boolean {
            val partial = p.getBoolean("q2_partial_restore", false)
            val brightnessKnown = (p.contains(b) && p.getInt(b, -1) in 0..255) ||
                (partial && p.getInt("q2_restored_brightness", -1) in 0..255)
            return brightnessKnown && ((p.contains(m) && p.getInt(m, -1) in 0..1) || (partial && p.getBoolean("q2_mode_restored", false)))
        }
        // Successful fields are removed independently. Documented partial recovery may have mode only.
        val missingPair = (manualOn && !validPair(manual, "prev_brightness", "prev_mode")) ||
            (screen && !validPair(shared, "screen_off_saver_prev_brightness", "screen_off_saver_prev_mode")) ||
            (saver && (!validPair(shared, "saver_prev_brightness", "saver_prev_mode") ||
                (!shared.contains("saver_prev_timeout") && !shared.getBoolean("q2_timeout_restored", false)))) ||
            (active && !shared.contains("active_saver_prev_timeout") && !shared.contains("active_saver_prev_sync"))
        if (unknownProfile || missingPair || listOf(active, screen, manualOn, saver).count { it } > 1) {
            ledger.edit().putString("legacy_error", "Older saver history has unknown or overlapping ownership. No settings were changed. Check Android display and auto-sync, then choose Keep current settings below. Original brightness for old profile/active/thermal sessions may be unrecoverable.").checkedCommit()
            return false
        }
        if (active) {
            restore(shared, "active_saver_prev_timeout", T, setOf(15000))
            sync(shared, "active_saver_prev_sync")
            ledger.edit().putString("legacy_notice", "Older active saver did not save original brightness. Exact recovery is unavailable; check display settings manually. Thermal originals held only in memory are also unavailable after a process restart.").checkedCommit()
            if (ok) shared.edit().putBoolean("active_saver_applied", false).checkedCommit()
        }
        fun coupledDisplay(p: android.content.SharedPreferences, brightnessKey: String, modeKey: String, applied: Int) {
            // One coupled decision avoids restoring adaptive mode after a later manual brightness edit.
            val recovering = p.getBoolean("q2_partial_restore", false)
            val restoredBrightness = p.getInt("q2_restored_brightness", -1)
            val ours = (recovering && runCatching { io.read(c, B) == applied || io.read(c, B) == restoredBrightness }.getOrDefault(false)) || runCatching { io.read(c, B) == applied && io.read(c, M) == 0 }.getOrDefault(false)
            if (ours) {
                check(save(p.edit().putBoolean("q2_partial_restore", true)))
                if (p.contains(brightnessKey)) check(save(p.edit().putInt("q2_restored_brightness", p.getInt(brightnessKey, -1))))
                restore(p, brightnessKey, B, setOf(applied))
                restore(p, modeKey, M, setOf(0))

            } else if (io.canWrite(c)) p.edit().putBoolean("q2_done_$brightnessKey", true).putBoolean("q2_done_$modeKey", true).checkedCommit()
            else ok = false
        }
        if (screen) {
            coupledDisplay(shared, "screen_off_saver_prev_brightness", "screen_off_saver_prev_mode", 10)
            sync(shared, "screen_off_saver_prev_sync")
            if (ok) shared.edit().putBoolean("screen_off_saver_applied", false).checkedCommit()
        }
        if (manualOn) {
            coupledDisplay(manual, "prev_brightness", "prev_mode", 40)
            if (ok) manual.edit().putBoolean("bright_on", false).checkedCommit()
        }
        if (saver) {
            coupledDisplay(shared, "saver_prev_brightness", "saver_prev_mode", 10)
            restore(shared, "saver_prev_timeout", T, setOf(15000))
            if (!shared.contains("saver_prev_timeout")) check(save(shared.edit().putBoolean("q2_timeout_restored", true)))
            if (ok) shared.edit().putBoolean("saver_display_changed", false).putBoolean("saver_active", false).checkedCommit()
        }
        if (ok) ledger.edit().remove("legacy_error").putBoolean("legacy_complete", true).checkedCommit()
        else if (!ledger.getBoolean("legacy_unknown", false)) ledger.edit().putString("legacy_error", "Older-version restore pending; allow Modify system settings and retry. New display changes are paused.").checkedCommit()
        return ok
    }

    private fun change(c: Context, key: String, value: Int) {
        val p = prefs(c)
        val current = io.read(c, key)
        if (current == value) return
        val edit = p.edit()
        if (!pending(c, key)) edit.putInt("before_$key", current)
        check(edit.putInt("applied_$key", value).checkedCommit())
        // Persist before calling Android, including a call that writes then throws.
        check(io.write(c, key, value))
        check(io.read(c, key) == value)
    }

    @Synchronized fun display(c: Context, owner: String, brightness: Int, timeoutMs: Int? = null): Boolean {
        if (persistenceFailed || !migrateLegacy(c) || !SavingConsent.displayAllowed(c) || !io.canWrite(c)) return false
        val p = prefs(c)
        val held = p.getString("display_owner", null)
        if (held != null && held != owner) return false
        return runCatching {
            // Retry a failed apply's rollback before interpreting any differences as manual edits.
            if (held != null && p.contains("display_error")) { restoreDisplay(c, owner); return false }
            if (held != null && reconcile(c)) return false
            check(p.edit().putString("display_owner", owner).remove("display_error").checkedCommit())
            change(c, M, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            change(c, B, brightness.coerceIn(0, 255))
            if (timeoutMs != null) change(c, T, timeoutMs)
            true
        }.getOrElse {
            runCatching { p.edit().putString("display_error", "Apply failed; restoration needs checking").checkedCommit() }
            restoreDisplay(c, owner)
            false
        }
    }

    @Synchronized fun pauseSync(c: Context, owner: String): Boolean {
        if (persistenceFailed || !migrateLegacy(c) || !SavingConsent.syncAllowed(c)) return false
        val p = prefs(c)
        val held = p.getString("sync_owner", null)
        if (held != null && held != owner) return false
        return runCatching {
            if (held != null && io.sync()) {
                p.edit().putBoolean("sync_external", true).checkedCommit()
                return false
            }
            if (p.getBoolean("sync_external", false)) return false
            if (held == null) check(p.edit().putString("sync_owner", owner).putBoolean("sync", io.sync()).checkedCommit())
            io.sync(false)
            check(!io.sync())
            p.edit().remove("sync_error").checkedCommit()
            true
        }.getOrElse {
            runCatching { p.edit().putString("sync_error", "Sync change failed; retry restoration").checkedCommit() }
            restoreSync(c, owner)
            false
        }
    }

    @Synchronized fun restoreDisplay(c: Context, owner: String?): Boolean = try {
        if (persistenceFailed) false else restoreDisplayImpl(c, owner)
    } catch (_: Exception) { persistenceFailed = true; false }
    private fun restoreDisplayImpl(c: Context, owner: String?): Boolean {
        val p = prefs(c)
        val held = p.getString("display_owner", null) ?: return true
        if (owner != null && owner != held) return false
        if (!io.canWrite(c)) {
            p.edit().putString("display_error", "Restore pending: allow Modify system settings, then retry").checkedCommit()
            return false
        }
        // A failed write may still equal its baseline. That is not a manual edit.
        runCatching {
            if (pending(c, B)) {
                val actual = io.read(c, B)
                if (actual != p.getInt("applied_$B", -1) && actual != p.getInt("before_$B", -1)) forget(c, M)
            }
        }
        var ok = true
        // Independent commits mean a failed mode restore cannot discard its baseline after brightness succeeds.
        for (key in fields) if (pending(c, key)) {
            val done = runCatching {
                val actual = io.read(c, key)
                val before = p.getInt("before_$key", actual)
                if (actual == p.getInt("applied_$key", -1) && actual != before) {
                    check(io.write(c, key, before))
                    check(io.read(c, key) == before)
                }
                check(forget(c, key))
                true
            }.getOrDefault(false)
            if (persistenceFailed) return false
            if (!done) ok = false
        }
        if (ok) p.edit().remove("display_owner").remove("display_external").remove("display_error").checkedCommit()
        else p.edit().putString("display_error", "Restore failed for some display settings; retry").checkedCommit()
        return ok
    }

    @Synchronized fun restoreSync(c: Context, owner: String?): Boolean = try {
        if (persistenceFailed) false else restoreSyncImpl(c, owner)
    } catch (_: Exception) { persistenceFailed = true; false }
    private fun restoreSyncImpl(c: Context, owner: String?): Boolean {
        val p = prefs(c)
        val held = p.getString("sync_owner", null) ?: return true
        if (owner != null && owner != held) return false
        return runCatching {
            if (!io.sync() && !p.getBoolean("sync_external", false)) {
                io.sync(p.getBoolean("sync", true))
                check(io.sync() == p.getBoolean("sync", true))
            }
            check(p.edit().remove("sync_owner").remove("sync_external").remove("sync_error").checkedCommit())
            true
        }.getOrElse {
            if (persistenceFailed) return false
            p.edit().putString("sync_error", "Auto-sync restore failed; retry").checkedCommit()
            false
        }
    }

    fun legacyNeedsChoice(c: Context) = prefs(c).getString("legacy_error", "")?.let { it.contains("unknown or overlapping") || it.contains("Unknown original") } == true

    /** Explicit UI acknowledgment only: current Android choices become the new baseline. */
    @Synchronized fun acceptLegacyCurrent(c: Context) {
        try { acceptLegacyCurrentImpl(c) } catch (_: Exception) { persistenceFailed = true }
    }
    private fun acceptLegacyCurrentImpl(c: Context) {
        val shared = c.getSharedPreferences("netra_sentinel_prefs", 0)
        val edit = shared.edit()
        listOf("active_saver_prev_timeout", "active_saver_prev_sync", "screen_off_saver_prev_brightness", "screen_off_saver_prev_mode", "screen_off_saver_prev_sync", "saver_prev_brightness", "saver_prev_mode", "saver_prev_timeout").forEach { edit.remove(it) }
        edit.putBoolean("active_saver_applied", false).putBoolean("screen_off_saver_applied", false).putBoolean("saver_display_changed", false).putBoolean("saver_active", false).checkedCommit()
        c.getSharedPreferences("netra_profile_applied", 0).edit().clear().checkedCommit()
        c.getSharedPreferences("netra_power_prefs", 0).edit().remove("prev_brightness").remove("prev_mode").putBoolean("bright_on", false).checkedCommit()
        prefs(c).edit().putBoolean("legacy_complete", true).remove("legacy_unknown").remove("legacy_error").putString("legacy_notice", "Current settings kept by your choice. Missing originals from older profile/active/thermal sessions cannot be recovered.").checkedCommit()
    }
    /** Every process starts a new saver session. Restore old sessions before any controller starts.
     * Controllers may then reevaluate real conditions and consent with a fresh baseline.
     */
    @Synchronized fun startNewProcessSessions(c: Context) {
        try { startNewProcessSessionsImpl(c) } catch (_: Exception) { persistenceFailed = true }
    }
    private fun startNewProcessSessionsImpl(c: Context) {
        check(commitGate())
        if (!migrateLegacy(c)) return
        val p = prefs(c)
        if (p.contains("display_owner")) check(p.edit().putString("display_error", "Previous process session ended; restore pending").checkedCommit())
        if (p.contains("sync_owner")) check(p.edit().putString("sync_error", "Previous process session ended; restore pending").checkedCommit())
        restoreDisplay(c, null)
        restoreSync(c, null)
        if (p.getBoolean("legacy_complete", false)) c.getSharedPreferences("netra_sentinel_prefs", 0).edit()
            .putBoolean("active_saver_applied", false).putBoolean("screen_off_saver_applied", false).putBoolean("saver_active", false).checkedCommit()
    }
    @Synchronized fun retryPending(c: Context) {
        try {
            // Exactly one attempt per event. Never call startup/retry recursively.
            val wasStorageFailure = persistenceFailed
            if (!commitGate()) return
            if (wasStorageFailure) prefs(c).edit().putBoolean("recovery_storage_probe", true).checkedCommit()
            persistenceFailed = false
            if (!migrateLegacy(c) || persistenceFailed) return
            val p = prefs(c)
            if (wasStorageFailure || p.contains("display_error") || !SavingConsent.displayAllowed(c)) restoreDisplay(c, null)
            if (wasStorageFailure || p.contains("sync_error") || !SavingConsent.syncAllowed(c)) restoreSync(c, null)
        } catch (_: Exception) { persistenceFailed = true }
    }
    fun restore(c: Context, owner: String) { restoreDisplay(c, owner); restoreSync(c, owner) }
    fun undo(c: Context) { migrateLegacy(c); restoreDisplay(c, null); restoreSync(c, null) }
    @Synchronized fun status(c: Context): String {
        val p = prefs(c)
        // Status is read-only: preference callbacks may run between write-ahead commit and Android I/O.
        val brightness = runCatching { "${io.read(c, B)}/255" }.getOrDefault("Unavailable")
        val timeout = runCatching { "${io.read(c, T) / 1000} s" }.getOrDefault("Unavailable")
        val sync = runCatching { if (io.sync()) "on" else "off" }.getOrDefault("Unavailable")
        val state = p.getString("display_error", null) ?: if (p.getBoolean("display_external", false)) "External display edit detected; saver no longer reapplies" else p.getString("display_owner", null)?.let { "Display owned by $it" } ?: "Display unchanged by savers"
        val syncState = p.getString("sync_error", null) ?: p.getString("sync_owner", null)?.let { "Sync owned by $it" } ?: "No sync restore pending"
        val recovery = if (persistenceFailed) "Settings storage failed. Automatic writes are blocked; retry recovery. " else ""
        val legacy = listOfNotNull(p.getString("legacy_error", null), p.getString("legacy_notice", null)).joinToString(" ")
        return "$recovery$legacy Actual brightness: $brightness; timeout: $timeout. Auto-sync: $sync. $state. $syncState."
    }
}
