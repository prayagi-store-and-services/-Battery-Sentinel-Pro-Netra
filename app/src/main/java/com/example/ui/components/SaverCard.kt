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
        }
        Text("Saver now: " + (if (SaverEngine.isActive(c)) "active" else "waiting"), fontSize = 11.sp)
        prefs.getString(SaverEngine.KEY_RESULT, null)?.let { Text(it, fontSize = 11.sp) }
    }
}
