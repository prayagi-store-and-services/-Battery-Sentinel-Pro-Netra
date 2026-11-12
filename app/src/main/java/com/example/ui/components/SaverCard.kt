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
    var cachedResult by remember { mutableStateOf("") }
    var riseOn by remember { mutableStateOf(prefs.getBoolean(SaverEngine.KEY_RISE_ON, true)) }
    var riseC by remember { mutableFloatStateOf(SaverPolicy.clampRise(prefs.getFloat(SaverEngine.KEY_RISE_C, SaverPolicy.DEFAULT_RISE_C))) }
    var riseMin by remember { mutableFloatStateOf(SaverPolicy.clampRiseMinTemp(prefs.getFloat(SaverEngine.KEY_RISE_MIN_TEMP, SaverPolicy.DEFAULT_RISE_MIN_TEMP_C))) }
    var journey by remember { mutableStateOf(SaverEngine.isJourneyOn(c)) }
    val canWrite = Settings.System.canWrite(c)

    SentinelCard(title = "Saver", icon = Icons.Default.SignalCellularAlt, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Save battery automatically", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                prefs.edit().putBoolean(SaverEngine.KEY_ENABLED, it).apply()
                if (!it) SaverEngine.restore(c)
            })
        }
        Text("Starts when the battery gets hot or low, and puts your settings back when it is normal again. Off by default.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Journey mode (up to 12 hours)", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = journey, onCheckedChange = { journey = it; SaverEngine.setJourney(c, it) })
        }
        Text("For a long trip. You turn it on yourself; it never starts alone and ends by itself after 12 hours. While it is on and the phone is not charging, the Saver actions below run at the next battery reading without waiting for the level limit, and they are put back when you plug in, turn it off or it ends. It uses only the battery readings the app already takes and adds no background work. It cannot stop other apps from using data or battery; on Android 13 and older it can close background processes (see the limits below); on Android 14 and newer it cannot.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Battery temperature: ${temp.toInt()} °C or more", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Row {
                OutlinedButton(onClick = { temp = SaverPolicy.clampTemp(temp - 1f); prefs.edit().putFloat(SaverEngine.KEY_TEMP, temp).apply() }) { Text("-") }
                OutlinedButton(onClick = { temp = SaverPolicy.clampTemp(temp + 1f); prefs.edit().putFloat(SaverEngine.KEY_TEMP, temp).apply() }) { Text("+") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Early start on fast temperature rise", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = riseOn, onCheckedChange = { riseOn = it; prefs.edit().putBoolean(SaverEngine.KEY_RISE_ON, it).apply() })
        }
        if (riseOn) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Rise of ${String.format(java.util.Locale.US, "%.1f", riseC)} °C within about 3 minutes", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Row {
                    OutlinedButton(onClick = { riseC = SaverPolicy.clampRise(riseC - 0.2f); prefs.edit().putFloat(SaverEngine.KEY_RISE_C, riseC).apply() }) { Text("-") }
                    OutlinedButton(onClick = { riseC = SaverPolicy.clampRise(riseC + 0.2f); prefs.edit().putFloat(SaverEngine.KEY_RISE_C, riseC).apply() }) { Text("+") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Only from ${riseMin.toInt()} °C up", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Row {
                    OutlinedButton(onClick = { riseMin = SaverPolicy.clampRiseMinTemp(riseMin - 1f); prefs.edit().putFloat(SaverEngine.KEY_RISE_MIN_TEMP, riseMin).apply() }) { Text("-") }
                    OutlinedButton(onClick = { riseMin = SaverPolicy.clampRiseMinTemp(riseMin + 1f); prefs.edit().putFloat(SaverEngine.KEY_RISE_MIN_TEMP, riseMin).apply() }) { Text("+") }
                }
            }
        }
        Text("Early start: the Saver also starts before the limit if the battery temperature rises fast, using the two values above, only when not charging, in this phone's own battery readings. It ends when the temperature stops rising, 5 minutes have passed and it is below the limit. Plugging in a charger never counts as a rise. It never closes an app you used in the last 15 minutes (it needs Usage Access to know) and leaves your screen timeout alone. Android cannot tell which app makes the heat, so the apps listed in the result are estimates from usage. It only reads the battery temperature; it cannot cool the phone itself.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Battery level: $level% or less (not charging)", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Row {
                OutlinedButton(onClick = { level = SaverPolicy.clampLevel(level - 1); prefs.edit().putInt(SaverEngine.KEY_LEVEL, level).apply() }) { Text("-") }
                OutlinedButton(onClick = { level = SaverPolicy.clampLevel(level + 1); prefs.edit().putInt(SaverEngine.KEY_LEVEL, level).apply() }) { Text("+") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness 10% and shortest screen timeout", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = actDisplay, onCheckedChange = { actDisplay = it; prefs.edit().putBoolean(SaverEngine.KEY_ACT_DISPLAY, it).apply() })
        }
        OutlinedButton(enabled = android.os.Build.VERSION.SDK_INT < 34, onClick = {
            SaverEngine.clearCachedProcesses(c) { result -> cachedResult = result }
        }) { Text("Request background-process cleanup") }
        Text("Manual only. Android 14+ cannot end other apps' processes. On older Android, apps may restart. This does not force-stop apps and makes no battery-saving promise.", fontSize = 11.sp)
        if (cachedResult.isNotEmpty()) Text(cachedResult, fontSize = 11.sp)
        Text("This app cannot read or clear other apps' notifications. Its own battery alerts still use Android's notification permission.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled && actDisplay && !canWrite) {
            Text("Modify system settings permission is needed for brightness and timeout. Until it is granted nothing is changed.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                c.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open permission screen") }
        }
        Text("Saver now: " + (if (SaverEngine.isActive(c)) "active" else "waiting"), fontSize = 11.sp)
        prefs.getString(SaverEngine.KEY_RESULT, null)?.takeUnless { it.contains("notification", ignoreCase = true) }?.let { Text(it, fontSize = 11.sp) }
    }
}
