package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
const val CHARGING_SCREEN_PREFS = PREFS
const val KEY_BACKGROUND = "auto_open_background"
const val KEY_SCREEN_ENABLED = "charging_screen_enabled"

/**
 * Optional charging screen, Portion 1. A plain black full screen with only real readings from the same
 * source as the Live Power card. The auto-open switch is ON by default and only reacts while the app is
 * on screen (an event from Android, no polling). Opening it while the app is hidden needs the overlay
 * permission and ships in Portion 2. Tap anywhere to close.
 */
@Composable
fun ChargingScreenCard() {
    val c = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(KEY_SCREEN_ENABLED, true)) }
    var auto by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO, true)) }
    var show by remember { mutableStateOf(false) }
    var bg by remember { mutableStateOf(prefs.getBoolean(KEY_BACKGROUND, true) && android.provider.Settings.canDrawOverlays(c)) }
    var bgNote by remember { mutableStateOf<String?>(null) }
    // Coming back from Android's "Display over other apps" screen: turn it on only if the permission was really granted.
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                val granted = android.provider.Settings.canDrawOverlays(c)
                if (prefs.contains(KEY_BACKGROUND) && prefs.getBoolean(KEY_BACKGROUND, true) && !granted) { prefs.edit().putBoolean(KEY_BACKGROUND, false).apply(); bg = false }
                if (bgNote != null && granted && !bg && prefs.getBoolean("bg_pending", false)) {
                    prefs.edit().putBoolean(KEY_BACKGROUND, true).putBoolean("bg_pending", false).apply(); bg = true; bgNote = null
                }
            }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    DisposableEffect(owner, auto, bg, enabled) {
        var receiver: BroadcastReceiver? = null
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START && enabled && auto && !bg && receiver == null) {
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

    SentinelCard(title = "Charging screen", icon = Icons.Default.Bolt, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Charging screen on (turn off to stop it opening by itself)", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; prefs.edit().putBoolean(KEY_SCREEN_ENABLED, it).apply() })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Open it when I plug in the charger (app must be open)", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = { auto = it; prefs.edit().putBoolean(KEY_AUTO, it).apply() })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Also open it when the app is closed or in the background (needs Display over other apps)", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = bg, onCheckedChange = { on ->
                if (!on) { prefs.edit().putBoolean(KEY_BACKGROUND, false).putBoolean("bg_pending", false).apply(); bg = false; bgNote = null }
                else if (android.provider.Settings.canDrawOverlays(c)) { prefs.edit().putBoolean(KEY_BACKGROUND, true).apply(); bg = true; bgNote = null }
                else {
                    prefs.edit().putBoolean("bg_pending", true).apply()
                    bgNote = "Unavailable until you allow Display over other apps for this app in the Android screen that just opened, then come back."
                    try {
                        c.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) { bgNote = "Unavailable: this phone has no Display over other apps screen." }
                }
            })
        }
        bgNote?.let { Text(it, fontSize = 12.sp) }
        ChargingDesignSettings()
        Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) { Text("Open charging screen now") }
        Text(
            "A black screen with the live battery values from this phone. It keeps the screen on while it is open (dimmed after 15 seconds), so close it with a double tap. Both switches are on by default; you can turn them off. After 15 seconds without a touch the screen dims to 10% brightness; a touch brings normal brightness back, and so does unplugging. " +
                "The background option uses the charger-connected event the app already listens for (no extra polling) and Android's Display over other apps permission, which only you can grant. It cannot open over a locked screen.",
            fontSize = 10.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (show) ChargingScreenDialog { show = false }
}

@Composable
private fun ChargingScreenDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ChargingScreenContent(onClose)
    }
}

/** The black screen itself. Used by the in-app dialog and by ChargingScreenActivity (opened from the background). */
@Composable
fun ChargingScreenContent(onClose: () -> Unit, keepScreenOn: Boolean = true) {
    val c = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val st = remember { ChargingStyleSettings.load(c) }
    var ui by remember { mutableStateOf<LivePowerUi?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                sharedTelemetry.targetPercent = com.example.NetraApplication.instance.settingsRepository.settings.value.chargeTargetPercent
                ui = sharedTelemetry.update(readSnapshot(c), SystemClock.elapsedRealtime())
                now = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // Dim to the chosen level (10% unless changed) after 15 seconds without a touch; a touch or closing the screen restores normal brightness.
    var lastTouchMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val win = remember(view) { windowOfView(view) }
    fun setBrightness(v: Float) { win?.let { w -> val a = w.attributes; a.screenBrightness = v; w.attributes = a } }
    val activeB = if (st.activePercent >= 100) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else st.activePercent / 100f
    val idleB = (minOf(st.dimPercent, st.activePercent) / 100f)
    LaunchedEffect(win) {
        var dimmed = false
        setBrightness(activeB)
        while (true) {
            val stillCharging = ui?.mode.let { it == null || it == com.example.service.PowerMode.CHARGING || it == com.example.service.PowerMode.FULL }
            val idle = stillCharging && SystemClock.elapsedRealtime() - lastTouchMs >= 15_000L
            if (idle && !dimmed) { setBrightness(idleB); dimmed = true }
            else if (!idle && dimmed) { setBrightness(activeB); dimmed = false }
            delay(250L)
        }
    }
    DisposableEffect(win) {
        onDispose { win?.let { w -> val a = w.attributes; a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = a } }
    }
    run {
        val u = ui
        val pct = u?.percentage?.trim()?.removeSuffix("%")?.toFloatOrNull()
        val watts = u?.power?.trim()?.removeSuffix(" W")?.toDoubleOrNull()
        val pctColor = styleColor(st, pct)
        val label = when (u?.mode) {
            PowerMode.CHARGING -> if (watts != null && watts >= FAST_CHARGE_WATTS) "FAST CHARGING" else "CHARGING"
            PowerMode.DISCHARGING -> "NOT ON CHARGER"
            PowerMode.FULL -> "FULL"
            PowerMode.NOT_CHARGING -> "NOT CHARGING"
            else -> "STATUS UNAVAILABLE"
        }
        Column(
            Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial); lastTouchMs = SystemClock.elapsedRealtime() } } }.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onClose() }) }.padding(24.dp),
            verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ChargingClockText(st.clock, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)))
                Text(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(now)), color = Color(0xFF9E9E9E), fontSize = 16.sp, maxLines = 1)
            }
            ChargingGauge(st.gauge, pct, pctColor, st.gaugeBrightness, if (pct != null) "${pct.toInt()}%" else (u?.percentage ?: "Calculating..."), label)
            val entries = ChargingStyleSettings.ITEMS.filter { it.first in st.items }.map { (key, name) ->
                when (key) {
                    "temp" -> name to (u?.temperature ?: "Unavailable")
                    "voltage" -> name to (u?.voltage ?: "Unavailable")
                    "watt" -> name to (u?.power ?: "Unavailable")
                    "current" -> name to (u?.current ?: "Unavailable")
                    "estimate" -> (u?.estimateLabel ?: name) to (u?.estimate ?: "Unavailable")
                    else -> name to (u?.sessionDuration ?: "Unavailable")
                }
            }
            ChargingDetailsBlock(st.details, entries)
            Text("Power is battery-side (voltage x current), not wall-adapter wattage. Double tap to close.", color = Color(0xFF757575), fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Shown as "Fast charging" only when the measured battery-side power is at or above this many watts. */
private const val FAST_CHARGE_WATTS = 15.0

@Composable
private fun ChargingStat(value: String, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 18.sp, maxLines = 1)
        Text(name, color = Color(0xFF9E9E9E), fontSize = 12.sp, maxLines = 1)
    }
}

/** Same colour steps as the battery colours elsewhere in the app: 75+ green, 50+ light green, 20+ amber, below 20 red. */
internal fun chargingLevelColor(pct: Float?): Color = when {
    pct == null -> Color(0xFF9E9E9E)
    pct >= 75f -> com.example.ui.theme.NetraEmerald
    pct >= 50f -> com.example.ui.theme.StatusGreen
    pct >= 20f -> com.example.ui.theme.StatusAmber
    else -> com.example.ui.theme.DangerRed
}

private fun windowOfView(view: android.view.View): android.view.Window? {
    (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.let { return it.window }
    var ctx: Context? = view.context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx.window
        ctx = ctx.baseContext
    }
    return null
}
