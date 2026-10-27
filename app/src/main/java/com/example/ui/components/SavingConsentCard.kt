package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.example.service.SavingConsent
import com.example.service.SavingSettings

@Composable
fun SavingConsentCard() {
    val c = LocalContext.current
    var display by remember { mutableStateOf(SavingConsent.displayAllowed(c)) }
    var sync by remember { mutableStateOf(SavingConsent.syncAllowed(c)) }
    var status by remember { mutableStateOf(SavingSettings.status(c)) }
    DisposableEffect(c) {
        val main = Handler(Looper.getMainLooper())
        val refresh = Runnable { status = SavingSettings.status(c) }
        fun queueRefresh() { main.removeCallbacks(refresh); main.post(refresh) }
        SavingSettings.migrateLegacy(c)
        val lifecycle = (c as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val resume = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                SavingSettings.retryPending(c)
                if (!SavingConsent.displayAllowed(c)) SavingSettings.restoreDisplay(c, null)
                if (!SavingConsent.syncAllowed(c)) SavingSettings.restoreSync(c, null)
                status = SavingSettings.status(c)
            }
        }
        lifecycle?.addObserver(resume)
        val prefs = c.getSharedPreferences("netra_saving_restore_v1", 0)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> queueRefresh() }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { queueRefresh() }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        c.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        val syncObserver = runCatching { android.content.ContentResolver.addStatusChangeListener(android.content.ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS) { queueRefresh() } }.getOrNull()
        onDispose { main.removeCallbacks(refresh); lifecycle?.removeObserver(resume); prefs.unregisterOnSharedPreferenceChangeListener(listener); c.contentResolver.unregisterContentObserver(observer); syncObserver?.let { runCatching { android.content.ContentResolver.removeStatusChangeListener(it) } } }
    }
    Column {
        Text("Automatic setting changes: separate consent")
        Text("Off by default, even if Android permission was granted before. Enabled savers may change only settings you allow. Previous values are saved and restored when the saver ends. At reapply or restore, different current values take priority. Edits that return to the saver value between checks cannot be detected.")
        Row(Modifier.fillMaxWidth()) {
            Text("Allow brightness and screen timeout", Modifier.weight(1f))
            Switch(checked = display, onCheckedChange = {
                SavingConsent.setDisplay(c, it); display = SavingConsent.displayAllowed(c)
                if (it && !PowerPrefs.canWrite(c)) PowerPrefs.openWriteSettings(c)
            })
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Allow pausing global auto-sync", Modifier.weight(1f))
            Switch(checked = sync, onCheckedChange = { SavingConsent.setSync(c, it); sync = SavingConsent.syncAllowed(c) })
        }
        Text("Auto-sync affects all accounts: email and other updates can be delayed. Turning this off restores the saved state.")
        Text(status)
        if (SavingSettings.legacyNeedsChoice(c)) OutlinedButton(onClick = { SavingSettings.acceptLegacyCurrent(c); status = SavingSettings.status(c) }) {
            Text("Keep current settings; discard older-version restore records")
        }
        OutlinedButton(onClick = { SavingSettings.retryPending(c); status = SavingSettings.status(c) }) { Text("Retry pending restores") }
        OutlinedButton(onClick = {
            SavingConsent.setDisplay(c, false); SavingConsent.setSync(c, false)
            display = SavingConsent.displayAllowed(c); sync = SavingConsent.syncAllowed(c); SavingSettings.undo(c); status = SavingSettings.status(c)
        }) { Text("Undo display/sync changes and stop display/sync changes") }
    }
}
