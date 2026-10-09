package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.service.DrainMeasure
import com.example.ui.theme.NetraCyan

private const val PREFS = "netra_booster"

private fun batteryNow(c: Context): Pair<Int, Boolean> {
    val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to bm.isCharging
}

/** Opens a system screen only when the phone has one for it; says so when it does not. */
private fun openIfAvailable(c: Context, intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (intent.resolveActivity(c.packageManager) != null) {
        try { c.startActivity(intent); true } catch (_: Exception) { false }
    } else false
}

@Composable
fun BoosterCard() {
    val c = LocalContext.current
    val prefs = remember { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var startLevel by remember { mutableLongStateOf(prefs.getInt("start_level", -1).toLong()) }
    var startMs by remember { mutableLongStateOf(prefs.getLong("start_ms", 0L)) }
    var startCharging by remember { mutableStateOf(prefs.getBoolean("start_charging", false)) }
    var message by remember { mutableStateOf("") }
    var tick by remember { mutableLongStateOf(0L) }

    SentinelCard(title = "Battery Booster", icon = Icons.Default.BatteryChargingFull, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        Text("This does not close other apps: since Android 14 an app can only end its own processes, so a \"cleaner\" cannot speed anything up. What helps is checking which apps use your battery and restricting the ones you do not need. These buttons open your phone's own screens.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                if (!openIfAvailable(c, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))) message = "This phone has no battery usage screen to open."
            }, modifier = Modifier.fillMaxWidth()) { Text("See which apps use battery") }
            OutlinedButton(onClick = {
                if (!openIfAvailable(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) message = "This phone has no battery optimisation screen to open."
            }, modifier = Modifier.fillMaxWidth()) { Text("Review battery restrictions") }
            OutlinedButton(onClick = {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
                if (!openIfAvailable(c, i)) message = "This phone has no app settings screen to open."
            }, modifier = Modifier.fillMaxWidth()) { Text("This app's settings") }
        }
        Text("Drain check", fontSize = 13.sp)
        Text("Tap Start, use the phone as normal for at least ${DrainMeasure.MIN_MINUTES} minutes, then tap Read. It shows only percent lost per hour. It cannot tell you which app caused it, and a result is only comparable with another run done under similar use.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = {
                val (lvl, chg) = batteryNow(c)
                val now = System.currentTimeMillis()
                prefs.edit().putInt("start_level", lvl).putLong("start_ms", now).putBoolean("start_charging", chg).apply()
                startLevel = lvl.toLong(); startMs = now; startCharging = chg; tick++
                message = ""
            }) { Text("Start") }
            OutlinedButton(onClick = { tick++ }) { Text("Read") }
        }
        val (lvlNow, chgNow) = remember(tick) { batteryNow(c) }
        val r = remember(tick, startMs) {
            DrainMeasure.result(startLevel.toInt(), startMs, startCharging, lvlNow, System.currentTimeMillis(), chgNow)
        }
        val line = when (r) {
            is DrainMeasure.Result.NotStarted -> "Not started."
            is DrainMeasure.Result.TooShort -> "Too soon to read: wait at least ${DrainMeasure.MIN_MINUTES} minutes."
            is DrainMeasure.Result.Invalid -> r.reason
            is DrainMeasure.Result.Ok -> "Lost ${r.dropPercent}% in ${r.minutes} min = about ${"%.1f".format(r.percentPerHour)}% per hour. Whole-percent readings, so short runs are rough."
        }
        Text(line, fontSize = 12.sp)
        if (message.isNotEmpty()) Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    }
}
