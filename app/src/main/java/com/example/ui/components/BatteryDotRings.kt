package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

/** Fixed slots, not a synthesized health estimate. Temperature removes one dot per degree C. */
internal fun ringDots(value: Float?): Int? = value?.takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 100)
internal fun temperatureRingDots(value: Float?): Int? = ringDots(value)?.let { 100 - it }
/**
 * Battery Health Life for the core ring. Capacity the phone itself reports
 * (full-charge vs design, [reportedHealthPct]) wins when present. Otherwise a
 * stress index from the latest readings: heat above 30 C, cold below 0 C and
 * deep discharge below 20% each remove points from 100. An estimate from the
 * latest temperature and level only, never a measured capacity; null when
 * there are no readings at all.
 */
internal fun healthLifePercent(level: Int?, temperatureC: Float?, reportedHealthPct: Int?): Float? {
    reportedHealthPct?.takeIf { it in 1..120 }?.let { return it.coerceIn(1, 100).toFloat() }
    var score = 100f
    var used = false
    temperatureC?.takeIf { it.isFinite() && it in -20f..80f }?.let { t ->
        used = true
        if (t > 30f) score -= ((t - 30f) * 2f).coerceAtMost(45f)
        if (t < 0f) score -= (-t * 2f).coerceAtMost(20f)
    }
    level?.takeIf { it in 0..100 }?.let { l ->
        used = true
        if (l < 20) score -= ((20 - l) * 1.5f).coerceAtMost(25f)
    }
    return if (used) score.coerceIn(5f, 100f) else null
}
internal fun ringCanAnimate(resumed: Boolean, visible: Boolean, interactive: Boolean, saving: Boolean, fresh: Boolean) =
    resumed && visible && interactive && !saving && fresh
internal fun ringRadialOffset(previous: Int, current: Int, progress: Float): Float =
    if (current > previous) 1f - progress.coerceIn(0f, 1f) else progress.coerceIn(0f, 1f)
internal fun ringColor(value: Float?, thermal: Boolean): Color = when {
    value == null || !value.isFinite() -> Color(0xFF7D8794)
    thermal && value >= 39.5f -> Color(0xFFF04452)
    thermal && value >= 38f -> Color(0xFFF4BA3D)
    thermal -> Color(0xFF35C890)
    value <= 15f -> Color(0xFFF04452)
    value <= 30f -> Color(0xFFF4BA3D)
    else -> Color(0xFF35C890)
}

@Composable
internal fun BatteryDotRings(
    level: Int?, temperature: Float?, fresh: Boolean, screenOn: Boolean,
    modifier: Modifier = Modifier, healthPercent: Float? = null, healthIsEstimate: Boolean = false,
    charging: Boolean? = null, animationsEnabled: Boolean = false
) {
    val c = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val power = remember(c) { c.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var interactive by remember { mutableStateOf(power.isInteractive) }
    var saving by remember { mutableStateOf(power.isPowerSaveMode) }
    var visible by remember { mutableStateOf(false) }
    DisposableEffect(owner, c) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            interactive = power.isInteractive
            saving = power.isPowerSaveMode
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                interactive = power.isInteractive
                saving = power.isPowerSaveMode
            }
        }
        owner.lifecycle.addObserver(observer)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        c.registerReceiver(receiver, filter)
        onDispose { owner.lifecycle.removeObserver(observer); c.unregisterReceiver(receiver) }
    }
    val values = listOf(level?.takeIf { it in 0..100 }?.toFloat(), temperature?.takeIf { it.isFinite() },
        healthPercent?.takeIf { it.isFinite() && it in 0f..100f })
    val counts = values.mapIndexed { ring, value -> if (ring == 1) temperatureRingDots(value) else ringDots(value) }
    var previous by remember { mutableStateOf(counts) }
    var origin by remember { mutableStateOf(counts) }
    val motion = remember { Animatable(1f) }
    val allowed = ringCanAnimate(resumed, visible, interactive && screenOn, saving, fresh)
    // Opt-in gauge animations: a frame clock runs only while the switch is on and the same
    // lifecycle/freshness gates as the ring transitions hold. Trends come from real readings.
    var fxTick by remember { mutableStateOf(0L) }
    val fxOn = animationsEnabled && allowed
    LaunchedEffect(fxOn) {
        if (!fxOn) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) { androidx.compose.runtime.withFrameNanos { fxTick = (it - start) / 33_000_000L } }
    }
    val tempTrend = remember { TrendTracker(0.3f) }
    val healthTrend = remember { TrendTracker(1f) }
    LaunchedEffect(temperature) { tempTrend.update(temperature) }
    LaunchedEffect(healthPercent) { healthTrend.update(healthPercent) }
    LaunchedEffect(counts, allowed) {
        // Only a measured change starts one finite transition. No idle/charging loop.
        val old = previous
        previous = counts
        origin = old
        if (allowed && old != counts && old.zip(counts).any { (a,b) -> a != null && b != null && a != b }) {
            motion.snapTo(0f)
            motion.animateTo(1f, tween(480))
        } else motion.snapTo(1f)
    }
    val batteryText = level?.takeIf { it in 0..100 }?.let { "$it%" } ?: "Unavailable"
    val tempText = temperature?.takeIf { it.isFinite() }?.let { String.format(java.util.Locale.getDefault(), "%.1f C", it) } ?: "Unavailable"
    val healthText = healthPercent?.takeIf { it.isFinite() && it in 0f..100f }?.let { "${it.roundToInt()}%" + if (healthIsEstimate) " est." else "" } ?: "Unavailable"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).onGloballyPositioned {
            val b = it.boundsInWindow()
            visible = b.width > 0f && b.height > 0f && b.bottom > 0f && b.right > 0f && b.top < view.height && b.left < view.width
        }.semantics { contentDescription = "100 dots each: outer battery $batteryText; middle temperature $tempText, ${counts[1]?.let { "$it dots remaining" } ?: "dots unavailable"}; inner health $healthText"
            stateDescription = if (allowed && motion.value < 1f) "Moving blue dots" else "Settled dots" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val unit = size.minDimension
                val radii = listOf(.46f, .39f, .32f)
                radii.forEachIndexed { ring, ratio ->
                    val count = counts[ring]
                    val old = origin[ring]
                    val progress = motion.value
                    val changed = allowed && progress < 1f && old != null && count != null && old != count
                    val settledColor = ringColor(values[ring], ring == 1)
                    repeat(100) { index ->
                        val angle = (-90 + index * 3.6) * PI / 180
                        val transitioning = changed && index in min(old!!, count!!) until max(old, count)
                        val lit = count != null && index < count
                        var radius = unit * ratio
                        if (transitioning) {
                            radius += unit * .055f * ringRadialOffset(old!!, count!!, progress)
                        }
                        val color = when {
                            transitioning -> Color(0xFF2699FF)
                            lit -> settledColor
                            else -> Color(0xFF7D8794).copy(alpha = .24f)
                        }
                        drawCircle(color, unit * .008f, center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat()))
                    }
                }
                if (fxOn) {
                    val t = fxTick
                    // Charging spark inside the outer ring band, only from the real charging state.
                    if (charging == true) drawSpark(center, unit, t)
                    // Temperature trend: rising -> fire, falling -> liquid, flat/unknown -> nothing.
                    when (tempTrend.current()) {
                        GaugeTrend.RISING -> drawFlames(center, unit, t)
                        GaugeTrend.FALLING -> drawWave(center, unit, t)
                        GaugeTrend.FLAT -> {}
                    }
                    // Health heartbeat inside the inner band; slowing and fading when the
                    // (often estimated) health trend falls, normal pulse otherwise.
                    drawEcg(center, unit, t, healthTrend.current() == GaugeTrend.FALLING)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 132.dp)) {
                Text(batteryText, fontSize = if (level == null) 14.sp else 28.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                Text(tempText, fontSize = 14.sp, color = ringColor(temperature, true))
                Text("Health", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(healthText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text("Outer: battery  |  Middle: temperature  |  Core: battery health life", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("100 dots each. Temperature drops dots as it heats. Health life: phone-reported or estimated (est.).", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
