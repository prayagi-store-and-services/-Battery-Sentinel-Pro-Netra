package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Real power preferences. A switch is only enabled when Android really lets this app do what it says. */
object PowerPrefs {
    private const val P = "netra_power_prefs"
    const val SAVING_BRIGHTNESS = 40 // of 255

    fun canWrite(c: Context) = Settings.System.canWrite(c)
    fun secureGranted(c: Context) = c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    private fun prefs(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)
    fun brightnessOn(c: Context) = c.getSharedPreferences("netra_saving_restore_v1", Context.MODE_PRIVATE).getString("display_owner", null) == "manual"
    fun saverOn(c: Context): Boolean = runCatching { Settings.Global.getInt(c.contentResolver, "low_power", 0) == 1 }.getOrDefault(false)

    fun openBatterySaver(c: Context) {
        runCatching { c.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    fun openWriteSettings(c: Context) {
        runCatching {
            c.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Explicit user action, still gated by the separate display consent. */
    fun setBrightness(c: Context, on: Boolean): Boolean {
        if (!on) return com.example.service.SavingSettings.restoreDisplay(c, "manual")
        if (!canWrite(c)) { openWriteSettings(c); return false }
        return com.example.service.SavingSettings.display(c, "manual", SAVING_BRIGHTNESS)
    }

}

@Composable
fun PowerPreferencesBody() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    val refresh = tick
    if (refresh < 0) return
    val canWrite = PowerPrefs.canWrite(context)
    val secure = false // No privileged Battery Saver path.
    val bright = PowerPrefs.brightnessOn(context)
    val saver = secure && PowerPrefs.saverOn(context)
    val profile = bright && (!secure || saver)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrefRow(
            "Saver profile preference",
            if (!canWrite) "Needs the \"Modify system settings\" permission first. Turn the switch to open that Android screen, allow it, then come back."
            else "Lowers brightness to a saving level" + (if (secure) " and turns on the system Battery Saver" else "; the system Battery Saver part is Unavailable (see below)") + ". Off restores your saved brightness and mode.",
            profile, true
        ) { on ->
            if (on) { PowerPrefs.setBrightness(context, true); if (secure) Unit }
            else { PowerPrefs.setBrightness(context, false); if (secure) Unit }
            tick++
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("System Battery Saver", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (secure) "Turns the phone's own Battery Saver on or off."
                    else "Android only lets you switch Battery Saver yourself. Tap to open the Battery Saver page.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (secure) Switch(checked = saver, onCheckedChange = { on -> Unit; tick++ })
            else androidx.compose.material3.TextButton(onClick = { PowerPrefs.openBatterySaver(context) }) { Text("Open") }
        }
        PrefRow(
            "Active Power Saving",
            "Forced from 11 PM to 7 AM, and after the screen has been off 15 minutes. With separate consent, requests dimming, 15 s screen timeout and auto-sync pause; restores saved settings at 7 AM or when the screen turns on. Android does not let apps switch off Wi-Fi, Bluetooth, mobile data or the network type, so no battery-saving figure is promised. If Netra Hub (same phone, offline) says you are driving, this pauses; thermal protection and the charge alarm stay on. " + com.example.service.ActivePowerSaver.statusText(context),
            com.example.service.ActivePowerSaver.isEnabled(context), canWrite
        ) { on -> com.example.service.ActivePowerSaver.setEnabled(context, on); tick++ }
        PrefRow(
            "Brightness preference",
            if (canWrite) "Sets brightness to a saving level (" + (PowerPrefs.SAVING_BRIGHTNESS * 100 / 255) + "%). Off restores your saved brightness and mode."
            else "Needs the \"Modify system settings\" permission. Turn the switch to open that Android screen and allow it.",
            bright, true
        ) { on -> PowerPrefs.setBrightness(context, on); tick++ }
    }
}

@Composable
private fun PrefRow(title: String, body: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(0.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
