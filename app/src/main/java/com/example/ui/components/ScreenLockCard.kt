package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ScreenLock

/** Opt-in: lock the screen when charging stops. Explains the permission first and lets the user take it back. */
@Composable
fun ScreenLockCard() {
    val c = LocalContext.current
    var enabled by remember { mutableStateOf(ScreenLock.isEnabled(c) && ScreenLock.isAdminActive(c)) }
    var active by remember { mutableStateOf(ScreenLock.isAdminActive(c)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        active = ScreenLock.isAdminActive(c)
        enabled = active
        ScreenLock.setEnabled(c, active)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lock the screen when charging stops", fontSize = 13.sp)
                Text(
                    "Off by default. When the charging screen is showing and charging stops (unplugged or paused), the phone locks and the screen goes off at once. " +
                        "This needs Android's Device Admin permission with one policy only: lock the screen. The app cannot read, change or erase anything on the phone. Nothing is stored or sent.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = { on ->
                if (on) {
                    if (ScreenLock.isAdminActive(c)) { ScreenLock.setEnabled(c, true); enabled = true }
                    else launcher.launch(ScreenLock.requestIntent(c))
                } else { ScreenLock.setEnabled(c, false); enabled = false }
            })
        }
        if (active) {
            OutlinedButton(onClick = { ScreenLock.removeAdmin(c); active = false; enabled = false }, modifier = Modifier.padding(top = 6.dp)) { Text("Remove the permission") }
            Text("While the permission is on, Android will not let you uninstall the app. Tap Remove first, or switch it off in Android Settings, Security, Device admin apps.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
