package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.service.SaverEngine
import com.example.service.SaverPolicy
import com.example.ui.theme.NetraCyan

@Composable
fun SaverCard() {
    val c = LocalContext.current
    val prefs = remember { c.getSharedPreferences(SaverEngine.PREFS, android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(SaverEngine.KEY_ENABLED, false)) }
    var temp by remember { mutableFloatStateOf(prefs.getFloat(SaverEngine.KEY_TEMP, SaverPolicy.DEFAULT_TEMP_C)) }
    var level by remember { mutableIntStateOf(prefs.getInt(SaverEngine.KEY_LEVEL, SaverPolicy.DEFAULT_LEVEL)) }
    var actDisplay by remember { mutableStateOf(prefs.getBoolean(SaverEngine.KEY_ACT_DISPLAY, true)) }
    var actKill by remember { mutableStateOf(prefs.getBoolean(SaverEngine.KEY_ACT_KILL, true)) }
    var actNotif by remember { mutableStateOf(prefs.getBoolean(SaverEngine.KEY_ACT_NOTIF, true)) }
    var journey by remember { mutableStateOf(SaverEngine.isJourneyOn(c)) }
    val canWrite = Settings.System.canWrite(c)
    val notifAccess = SaverEngine.hasNotificationAccess(c)

    SentinelCard(title = "Saver", icon = Icons.Default.SignalCellularAlt, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Save battery automatically", fontSize = 13.sp)
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                prefs.edit().putBoolean(SaverEngine.KEY_ENABLED, it).apply()
                if (!it) SaverEngine.restore(c)
            })
        }
        Text("Starts when the battery gets hot or low, and puts your settings back when it is normal again. Off by default.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Journey mode (up to 12 hours)", fontSize = 13.sp)
            Switch(checked = journey, onCheckedChange = { journey = it; SaverEngine.setJourney(c, it) })
        }
        Text("For a long trip. You turn it on yourself; it never starts alone and ends by itself after 12 hours. While it is on and the phone is not charging, the Saver actions below run at the next battery reading without waiting for the level limit, and they are put back when you plug in, turn it off or it ends. It uses only the battery readings the app already takes and adds no background work. It cannot stop other apps from using data or battery; it can only close background processes (see the limits below).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Battery temperature: ${temp.toInt()} °C or more", fontSize = 13.sp)
            Row {
                OutlinedButton(onClick = { temp = SaverPolicy.clampTemp(temp - 1f); prefs.edit().putFloat(SaverEngine.KEY_TEMP, temp).apply() }) { Text("-") }
                OutlinedButton(onClick = { temp = SaverPolicy.clampTemp(temp + 1f); prefs.edit().putFloat(SaverEngine.KEY_TEMP, temp).apply() }) { Text("+") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Battery level: $level% or less (not charging)", fontSize = 13.sp)
            Row {
                OutlinedButton(onClick = { level = SaverPolicy.clampLevel(level - 1); prefs.edit().putInt(SaverEngine.KEY_LEVEL, level).apply() }) { Text("-") }
                OutlinedButton(onClick = { level = SaverPolicy.clampLevel(level + 1); prefs.edit().putInt(SaverEngine.KEY_LEVEL, level).apply() }) { Text("+") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness 10% and shortest screen timeout", fontSize = 13.sp)
            Switch(checked = actDisplay, onCheckedChange = { actDisplay = it; prefs.edit().putBoolean(SaverEngine.KEY_ACT_DISPLAY, it).apply() })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Close background apps", fontSize = 13.sp)
            Switch(checked = actKill, onCheckedChange = { actKill = it; prefs.edit().putBoolean(SaverEngine.KEY_ACT_KILL, it).apply() })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Clear notifications", fontSize = 13.sp)
            Switch(checked = actNotif, onCheckedChange = { actNotif = it; prefs.edit().putBoolean(SaverEngine.KEY_ACT_NOTIF, it).apply() })
        }
        Text("Android only lets an app end the background processes of other apps. Music, navigation, calls, messaging, keyboard, launcher and Netra apps are not touched, and some apps restart by themselves, so the saving can be small. The result below shows real free RAM before and after. Closed apps and cleared notifications cannot be brought back; brightness and timeout are restored.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled && actDisplay && !canWrite) {
            Text("Modify system settings permission is needed for brightness and timeout. Until it is granted nothing is changed.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                c.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open permission screen") }
        }
        if (enabled && actNotif && !notifAccess) {
            Text("Notification access is needed to clear notifications. We never read their content.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open notification access") }
            if (restrictedSettingsLikely(c)) {
                Text("If the switch \"Allow notification access\" is grey or will not turn on: Android 13 and newer block this for apps installed from a file (APK) instead of a store. This app cannot unlock it for you. Easiest fix: install the next update with \"Check for update\" inside this app, because that install is not blocked. Or do this once:", fontSize = 12.sp)
                Text("1. Tap \"Open app info\" below.\n2. Tap the three dots at the top right and choose \"Allow restricted settings\". On some phones (Realme, Oppo, OnePlus) this item shows only after you have tried to switch notification access on once, so try the switch first, then come back here.\n3. Confirm with your PIN or fingerprint if asked.\n4. Tap \"Open notification access\" above and switch it on for this app.\nMenu names can differ a little on your phone.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open app info") }
            }
        }
        Text("Saver now: " + (if (SaverEngine.isActive(c)) "active" else "waiting"), fontSize = 11.sp)
        prefs.getString(SaverEngine.KEY_RESULT, null)?.let { Text(it, fontSize = 11.sp) }
    }
}

/**
 * True when Android 13+ may block notification access ("Restricted setting") for this app: the app was not installed by
 * Google Play or by this app's own updater (the updater uses a PackageInstaller session, which Android does not restrict). Android has no public call that says whether the block is active, so this only decides whether to SHOW the
 * steps; the steps say "if the switch is grey".
 */
internal fun restrictedSettingsLikely(c: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 33) return false
    return try {
        val installer = c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName
        installer != "com.android.vending" && installer != c.packageName
    } catch (e: Exception) { true }
}
