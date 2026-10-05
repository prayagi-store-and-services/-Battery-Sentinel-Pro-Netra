package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** One permission row: name, plain reason, live status read from Android, and the Android page a tap opens. */
private class BsPerm(val name: String, val reason: String, val status: (Context) -> String, val open: ((Context) -> Unit)?)

private fun appDetails(c: Context) {
    c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun tryOpen(c: Context, i: Intent) {
    try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { appDetails(c) }
}

private fun runtime(c: Context, p: String): String = try {
    if (c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

private fun runtimeFrom(sdk: Int, c: Context, p: String): String =
    if (Build.VERSION.SDK_INT >= sdk) runtime(c, p) else "Not needed on this Android version"

private fun overlayStatus(c: Context): String = try {
    if (Settings.canDrawOverlays(c)) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

private fun writeSettingsStatus(c: Context): String = try {
    if (Settings.System.canWrite(c)) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

private fun batteryStatus(c: Context): String = try {
    if ((c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)) "Allowed (unrestricted)" else "Not allowed (Android may limit background work)"
} catch (e: Exception) { "Unavailable" }

private fun installStatus(c: Context): String = try {
    if (Build.VERSION.SDK_INT < 26) "Not needed on this Android version"
    else if (c.packageManager.canRequestPackageInstalls()) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

private fun bsPermissions(): List<BsPerm> = listOf(
    BsPerm("Notifications", "Used to show battery alerts and the running-service notice.",
        { runtimeFrom(33, it, android.Manifest.permission.POST_NOTIFICATIONS) }, { appDetails(it) }),
    BsPerm("Location", "Used for weather and climate context and to work out your country for region-aware features. Not stored on a server.",
        { runtime(it, android.Manifest.permission.ACCESS_FINE_LOCATION) }, { appDetails(it) }),
    BsPerm("Nearby devices (Bluetooth)", "Used to show connected Bluetooth devices and their battery on the Devices screen.",
        { runtimeFrom(31, it, android.Manifest.permission.BLUETOOTH_CONNECT) }, { appDetails(it) }),
    BsPerm("Phone state", "Used to read the mobile network type (for example 4G or 5G) in the network features. Calls and numbers are not read.",
        { runtime(it, android.Manifest.permission.READ_PHONE_STATE) }, { appDetails(it) }),
    BsPerm("Display over other apps", "Used by the optional charging screen overlay.",
        { overlayStatus(it) }, { tryOpen(it, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + it.packageName))) }),
    BsPerm("Modify system settings", "Used by the savers to lower brightness and screen timeout when you switch them on.",
        { writeSettingsStatus(it) }, { tryOpen(it, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + it.packageName))) }),
    BsPerm("Secure settings", "Used by the network-mode switch and the low-power preference. Android allows it only through a one-time computer (adb) step, so these two controls stay unavailable until then.",
        { runtime(it, "android.permission.WRITE_SECURE_SETTINGS").replace("Allowed", "Granted").replace("Not allowed", "Not granted") }, null),
    BsPerm("Battery optimisation", "Lets the battery service keep running in the background. Tap to change it on the Android page.",
        { batteryStatus(it) }, { tryOpen(it, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }),
    BsPerm("Install apps", "Used only when you tap Install on an update, so Android can install the new Battery Sentinel file.",
        { installStatus(it) }, { if (Build.VERSION.SDK_INT >= 26) tryOpen(it, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + it.packageName))) else appDetails(it) }),
    BsPerm("Internet and network state", "Used to check for updates and festival/region data. Always allowed by Android (normal permission).", { "Always allowed" }, null),
    BsPerm("Run at start, vibrate, foreground service, close background apps, sync settings", "Used so monitoring restarts after a reboot, shows alerts, runs as a service, lets the saver close idle background apps and pauses auto-sync in the screen-off saver. Normal permissions, always allowed.", { "Always allowed" }, null)
)

/** Settings card listing every permission the app declares. Status is re-read when the screen resumes; no timer, no background work. */
@Composable
fun BsPermissionsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val lc = (context as? ComponentActivity)?.lifecycle
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lc?.addObserver(obs)
        onDispose { lc?.removeObserver(obs) }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Permissions", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("What the app uses and why. Tap a row to open its Android page.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            bsPermissions().forEach { p ->
                val st = remember(tick) { p.status(context) }
                val m = if (p.open != null) Modifier.fillMaxWidth().clickable { p.open.invoke(context) } else Modifier.fillMaxWidth()
                Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.name + "  -  " + st, fontWeight = FontWeight.SemiBold)
                    Text(p.reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
