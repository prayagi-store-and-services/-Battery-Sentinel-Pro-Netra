package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.service.NetworkSwitchCoordinator as N
import com.example.service.RadioClass
import com.example.ui.theme.NetraCyan

private fun fmt(ms: Long): String {
    val m = ms / 60000
    return "${m / 60}h ${m % 60}m"
}

@Composable
fun NetworkSavingCard() {
    val c = LocalContext.current
    val prefs = remember { c.getSharedPreferences("netra_sentinel_prefs", android.content.Context.MODE_PRIVATE) }
    var suggest by remember { mutableStateOf(prefs.getBoolean(N.KEY_SUGGEST_ENABLED, false)) }
    var experiment by remember { mutableStateOf(prefs.getBoolean(N.KEY_EXPERIMENT_ENABLED, false)) }
    val permReq = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    SentinelCard(title = "Network Saving (screen off)", icon = Icons.Default.SignalCellularAlt, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Suggest lower network when screen is off", fontSize = 13.sp)
            Switch(checked = suggest, onCheckedChange = {
                suggest = it; prefs.edit().putBoolean(N.KEY_SUGGEST_ENABLED, it).apply()
                if (it && !N.hasPhoneStatePermission(c)) permReq.launch(android.Manifest.permission.READ_PHONE_STATE)
                if (it && !com.example.util.PermissionHelper.isUsageAccessGranted(c)) com.example.util.PermissionHelper.openUsageAccessSettings(c)
            })
        }
        Text("5G to 4G, 4G to 3G. Skipped if about 200 MB or more of mobile data was used in the last 10 minutes. Android does not let apps switch the network, so you get a notification to tap. Needs Phone State and Usage Access.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Experimental: auto-switch via ADB grant", fontSize = 13.sp)
            Switch(checked = experiment, onCheckedChange = { experiment = it; prefs.edit().putBoolean(N.KEY_EXPERIMENT_ENABLED, it).apply() })
        }
        Text("Unverified, may do nothing on your phone. Needs a one-time command from a computer: adb shell pm grant ${c.packageName} android.permission.WRITE_SECURE_SETTINGS. Restores on screen on.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Secure-setting access: ${if (N.hasSecureSettingsPermission(c)) "granted" else "not granted"}", fontSize = 11.sp)
        prefs.getString(N.KEY_LAST_RESULT, null)?.let { Text("Last experiment: $it", fontSize = 11.sp) }
        Text("TIME ON NETWORK TYPE (measured while Netra runs)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(RadioClass.NR_5G to "5G", RadioClass.LTE_4G to "4G", RadioClass.THREE_G_OR_LOWER to "3G or lower", RadioClass.UNKNOWN to "Unknown").forEach { (k, l) ->
            Text("$l: ${fmt(N.radioTimeMs(c, k))}", fontSize = 12.sp)
        }
        Text("Battery use per network type is not exposed by Android, so only time is shown.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
