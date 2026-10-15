package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.model.DotState
import com.example.service.BatterySnapshot
import com.example.service.LivePowerTelemetry
import com.example.service.LivePowerUi
import com.example.service.PowerMode
import com.example.ui.theme.NetraCyan
import kotlinx.coroutines.delay

/** One process-wide telemetry state, so rotation does not restart the session timer. */
internal val sharedTelemetry = LivePowerTelemetry()

internal fun readSnapshot(c: Context): BatterySnapshot {
    val i: Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    fun extra(name: String, absent: Int = Int.MIN_VALUE): Int? = i?.getIntExtra(name, absent)?.takeIf { it != absent }
    val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val raw = try { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Exception) { null }
    return BatterySnapshot(
        status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN,
        plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
        level = extra(BatteryManager.EXTRA_LEVEL, -1),
        scale = extra(BatteryManager.EXTRA_SCALE, -1),
        voltageMv = extra(BatteryManager.EXTRA_VOLTAGE, 0),
        temperatureTenthsC = extra(BatteryManager.EXTRA_TEMPERATURE),
        rawCurrentMicroAmps = raw
    )
}

/** Single loop, only while the screen is visible (STARTED). It stops in the background, so nothing polls when the app is hidden. */
@Composable
fun LivePowerCard() {
    val c = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var ui by remember { mutableStateOf<LivePowerUi?>(null) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                sharedTelemetry.targetPercent = com.example.NetraApplication.instance.settingsRepository.settings.value.chargeTargetPercent
                ui = sharedTelemetry.update(readSnapshot(c), SystemClock.elapsedRealtime())
                delay(1000L)
            }
        }
    }
    ui?.let { LivePowerContent(it) }
}

/** Production content also used by screenshot tests; no hardware or invented readings in previews. */
@Composable
fun LivePowerContent(u: LivePowerUi) {
    SentinelCard(title = if (u.current != LivePowerTelemetry.UNAVAILABLE) "Live battery current" else "Live battery readings", icon = Icons.Default.Bolt, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        val currentAvailable = u.current != LivePowerTelemetry.UNAVAILABLE
        if (currentAvailable) {
            Text(u.current, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Text(when (u.mode) {
            PowerMode.CHARGING -> "Charging"
            PowerMode.DISCHARGING -> "Discharging"
            PowerMode.FULL -> "Full"
            PowerMode.NOT_CHARGING -> "Not charging"
            PowerMode.UNKNOWN -> "Status unknown"
        }, fontSize = 16.sp)
        if (currentAvailable) u.currentStats?.let { stats ->
            Text("Observed samples: ${stats.samples}", fontSize = 12.sp)
            Text("Min ${formatMa(stats.minimumMa)}  |  Max ${formatMa(stats.maximumMa)}", fontSize = 13.sp)
            Text("Sample average ${formatMa(stats.averageMa)}", fontSize = 13.sp)
        }
        PowerRow("Voltage", u.voltage, "Battery-side power", u.power)
        PowerRow("Temperature", u.temperature, "Battery", u.percentage)
        PowerRow("Session Duration", u.sessionDuration, u.estimateLabel ?: "Estimate", u.estimate ?: "Unavailable")
        Text(
            "Current updates every second while this screen is visible. Values are Android-reported battery current, not charger output. " +
                "Magnitude is shown because phones differ in sign. Min, max and average describe observed samples only; they reset on a mode change, missing reading or sampling gap over 5 seconds. " +
                "OEM current reporting can be inaccurate. Battery-side watts are calculated from voltage x current, not wall-adapter watts. " +
                "Session time starts when this screen sees the mode. Estimates use the last 10 minutes of battery percentage progress.",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMa(ma: Double): String = String.format(java.util.Locale.US, "%.2f mA", ma)

@Composable
private fun PowerRow(a: String, av: String, b: String, bv: String) {
    val showA = av != "Unavailable"
    val showB = bv != "Unavailable"
    if (!showA && !showB) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (showA && showB) Arrangement.SpaceBetween else Arrangement.Start) {
        if (showA) Text("$a: $av", fontSize = 14.sp)
        if (showB) Text("$b: $bv", fontSize = 14.sp)
    }
}
