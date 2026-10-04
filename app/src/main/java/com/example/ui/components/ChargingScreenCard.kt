package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.model.DotState
import com.example.service.LivePowerUi
import com.example.service.PowerMode
import com.example.ui.theme.NetraCyan
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS = "netra_charging_screen"
private const val KEY_AUTO = "auto_open_on_plug"

/**
 * Optional charging screen, Portion 1. A plain black full screen with only real readings from the same
 * source as the Live Power card. The auto-open switch is OFF by default and only reacts while the app is
 * on screen (an event from Android, no polling). Opening it while the app is hidden needs the overlay
 * permission and ships in Portion 2. Tap anywhere to close.
 */
@Composable
fun ChargingScreenCard() {
    val c = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var auto by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO, false)) }
    var show by remember { mutableStateOf(false) }

    DisposableEffect(owner, auto) {
        var receiver: BroadcastReceiver? = null
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START && auto && receiver == null) {
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) { show = true }
                }
                c.registerReceiver(receiver, IntentFilter(Intent.ACTION_POWER_CONNECTED))
            } else if (e == Lifecycle.Event.ON_STOP) {
                receiver?.let { try { c.unregisterReceiver(it) } catch (_: Exception) {} }
                receiver = null
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            receiver?.let { try { c.unregisterReceiver(it) } catch (_: Exception) {} }
        }
    }

    SentinelCard(title = "Charging screen (optional)", icon = Icons.Default.Bolt, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Open it when I plug in the charger (app must be open)", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = { auto = it; prefs.edit().putBoolean(KEY_AUTO, it).apply() })
        }
        Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) { Text("Open charging screen now") }
        Text(
            "A black screen with the live battery values from this phone. It keeps the screen on while it is open, so close it with a tap. Off by default. " +
                "Opening it when the app is in the background needs a permission and is not part of this version.",
            fontSize = 10.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (show) ChargingScreenDialog { show = false }
}

@Composable
private fun ChargingScreenDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var ui by remember { mutableStateOf<LivePowerUi?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                ui = sharedTelemetry.update(readSnapshot(c), SystemClock.elapsedRealtime())
                now = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val u = ui
        Column(
            Modifier.fillMaxSize().background(Color.Black).clickable { onClose() }.padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now)), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(now)), color = Color(0xFF9E9E9E), fontSize = 14.sp)
            Text(u?.percentage ?: "Calculating...", color = NetraCyan, fontSize = 72.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
            Text(
                when (u?.mode) {
                    PowerMode.CHARGING -> "Charging"
                    PowerMode.DISCHARGING -> "Not on charger"
                    PowerMode.FULL -> "Full"
                    PowerMode.NOT_CHARGING -> "Not charging"
                    else -> "Status unknown"
                },
                color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center
            )
            if (u != null) {
                val rows = listOf("Power" to u.power, "Voltage" to u.voltage, "Current" to u.current, "Temperature" to u.temperature,
                    (u.estimateLabel ?: "Estimate") to (u.estimate ?: "Unavailable"))
                rows.forEach { (k, v) ->
                    if (v != "Unavailable") Text("$k: $v", color = Color(0xFFE0E0E0), fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Text("Power is calculated battery-side (voltage x current), not wall-adapter wattage.", color = Color(0xFF757575), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
            }
            Text("Tap anywhere to close", color = Color(0xFF616161), fontSize = 11.sp, modifier = Modifier.padding(top = 20.dp))
        }
    }
}
