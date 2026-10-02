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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.service.ScreenOffSaver
import com.example.ui.theme.NetraCyan

@Composable
fun ScreenOffSaverCard() {
    val c = LocalContext.current
    val prefs = remember { c.getSharedPreferences("netra_sentinel_prefs", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(ScreenOffSaver.KEY_ENABLED, false)) }
    val canWrite = ScreenOffSaver.canWriteSettings(c)

    SentinelCard(title = "Charging + screen off savings", icon = Icons.Default.SignalCellularAlt, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Lower brightness and pause auto-sync", fontSize = 13.sp)
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                prefs.edit().putBoolean(ScreenOffSaver.KEY_ENABLED, it).apply()
                if (!it) ScreenOffSaver.restore(c)
            })
        }
        Text("Only while the phone is charging and the screen is off. Your previous brightness and auto-sync setting are put back when the screen turns on or you unplug. Off by default.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled && !canWrite) {
            Text("Iski permission required hai - kripya on karein: Modify system settings. Jab tak permission nahin milti, kuch change nahin hota.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                c.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open permission screen") }
        } else {
            Text("Modify system settings permission: " + (if (canWrite) "granted" else "not granted"), fontSize = 11.sp)
        }
    }
}
