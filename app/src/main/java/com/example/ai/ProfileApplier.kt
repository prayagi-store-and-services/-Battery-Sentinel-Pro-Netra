package com.example.ai

import android.content.Context
import com.example.model.PowerProfileMode
import com.example.service.SavingSettings

/** Profile controls require separate opt-ins and share a persistent restore point. */
object ProfileApplier {
    data class Result(val brightness: String, val sync: String, val timeout: String)
    fun apply(c: Context, mode: PowerProfileMode): Result {
        val saving = mode == PowerProfileMode.ENDURANCE || mode == PowerProfileMode.ULTRA_SAVER
        if (!saving) {
            SavingSettings.restore(c, "profile")
            val state = SavingSettings.status(c)
            return Result(state, state, state)
        }
        val ultra = mode == PowerProfileMode.ULTRA_SAVER
        val level = if (ultra) 38 else 102
        val ms = if (ultra) 15000 else 30000
        val applied = SavingSettings.display(c, "profile", level, ms)
        val sync = if (ultra) SavingSettings.pauseSync(c, "profile") else {
            SavingSettings.restoreSync(c, "profile"); false
        }
        return Result(if (applied) "Applied: $level/255" else "Not applied: consent, permission or another saver",
            if (sync) "Applied: auto-sync off" else "Not changed (consent required)",
            if (applied) "Applied: ${ms / 1000} s" else "Not changed")
    }
}
