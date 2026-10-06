package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Saved choices for the charging screen look. Everything is stored on this phone only. */
data class ChargingStyleSettings(
    val clock: Int = 3,
    val gauge: Int = 0,
    val details: Int = 0,
    val color: Int = 0,
    val gaugeBrightness: Float = 1f,
    val dimPercent: Int = 10,
    val activePercent: Int = 30,
    val items: Set<String> = setOf("temp", "time", "estimate", "watt")
) {
    companion object {
        const val PREFS = "netra_charging_style"
        val CLOCKS = listOf("Off", "Light", "Regular", "Bold", "Outline", "Mono")
        val GAUGES = listOf("Ring", "Dotted ring", "Arc", "Bars", "Battery bar", "Number only")
        val DETAILS = listOf("Plain", "Boxed", "One line")
        val COLORS = listOf(
            "Auto" to Color.Unspecified, "Red" to Color(0xFFFF1744), "Pink" to Color(0xFFE91E63), "Purple" to Color(0xFF9C27B0),
            "Blue" to Color(0xFF2979FF), "Cyan" to Color(0xFF00E5FF), "Green" to Color(0xFF00E676), "Amber" to Color(0xFFFFB300), "White" to Color(0xFFFFFFFF)
        )
        val ITEMS = listOf("temp" to "Temp", "voltage" to "Voltage", "watt" to "Wattage", "current" to "Current", "estimate" to "Estimate", "time" to "Charging time")

        fun load(c: Context): ChargingStyleSettings {
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = ChargingStyleSettings()
            val items = p.getStringSet("items", null)?.toSet() ?: d.items
            return ChargingStyleSettings(
                clock = p.getInt("clock", d.clock).coerceIn(0, CLOCKS.size - 1),
                gauge = p.getInt("gauge", d.gauge).coerceIn(0, GAUGES.size - 1),
                details = p.getInt("details", d.details).coerceIn(0, DETAILS.size - 1),
                color = p.getInt("color", d.color).coerceIn(0, COLORS.size - 1),
                gaugeBrightness = p.getFloat("gaugeBrightness", d.gaugeBrightness).coerceIn(0.3f, 1f),
                dimPercent = p.getInt("dimPercent", d.dimPercent).coerceIn(5, 50),
                activePercent = p.getInt("activePercent", d.activePercent).coerceIn(5, 100),
                items = items
            )
        }

        fun save(c: Context, s: ChargingStyleSettings) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("clock", s.clock).putInt("gauge", s.gauge).putInt("details", s.details).putInt("color", s.color)
                .putFloat("gaugeBrightness", s.gaugeBrightness).putInt("dimPercent", s.dimPercent).putInt("activePercent", s.activePercent)
                .putStringSet("items", s.items).apply()
        }
    }
}

/** The colour to draw with: Auto follows the battery level, the others are the user's pick. */
internal fun styleColor(s: ChargingStyleSettings, pct: Float?): Color {
    val picked = ChargingStyleSettings.COLORS[s.color].second
    return if (picked == Color.Unspecified) chargingLevelColor(pct) else picked
}

@Composable
internal fun ChargingClockText(style: Int, text: String) {
    when (style) {
        0 -> {}
        1 -> Text(text, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light, maxLines = 1)
        2 -> Text(text, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Normal, maxLines = 1)
        3 -> Text(text, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        4 -> Text(text, style = TextStyle(color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, drawStyle = Stroke(width = 3f)), maxLines = 1)
        else -> Text(text, color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
internal fun ChargingGauge(style: Int, pct: Float?, color: Color, brightness: Float, centerText: String, label: String) {
    val lit = color.copy(alpha = brightness)
    val dark = color.copy(alpha = 0.2f)
    val frac = (pct ?: 0f).coerceIn(0f, 100f) / 100f
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (style) {
                0 -> {
                    val sw = 14.dp.toPx()
                    drawArc(dark, -90f, 360f, false, style = Stroke(sw, cap = StrokeCap.Round))
                    if (pct != null) drawArc(lit, -90f, 360f * frac, false, style = Stroke(sw, cap = StrokeCap.Round))
                }
                1 -> {
                    val n = 60
                    val r = w / 2f - 10.dp.toPx()
                    val litCount = (n * frac).toInt()
                    for (i in 0 until n) {
                        val a = Math.toRadians((-90.0 + i * 360.0 / n))
                        val cx = w / 2f + (r * cos(a)).toFloat()
                        val cy = h / 2f + (r * sin(a)).toFloat()
                        drawCircle(if (pct != null && i < litCount) lit else dark, 3.dp.toPx(), Offset(cx, cy))
                    }
                }
                2 -> {
                    val sw = 14.dp.toPx()
                    drawArc(dark, 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round))
                    if (pct != null) drawArc(lit, 135f, 270f * frac, false, style = Stroke(sw, cap = StrokeCap.Round))
                }
                3 -> {
                    val n = 10
                    val gap = 6.dp.toPx()
                    val bw = (w - gap * (n - 1)) / n
                    val litCount = Math.ceil((n * frac).toDouble()).toInt()
                    for (i in 0 until n) {
                        val bh = h * (0.2f + 0.8f * (i + 1) / n)
                        drawRoundRect(if (pct != null && i < litCount) lit else dark, Offset(i * (bw + gap), h - bh), Size(bw, bh), CornerRadius(4.dp.toPx()))
                    }
                }
                4 -> {
                    val bh = h * 0.45f
                    val top = (h - bh) / 2f
                    drawRoundRect(dark, Offset(0f, top), Size(w, bh), CornerRadius(16.dp.toPx()))
                    if (pct != null) drawRoundRect(lit, Offset(0f, top), Size(w * frac, bh), CornerRadius(16.dp.toPx()))
                }
                else -> {}
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, color = if (style == 4) Color.White else color, fontSize = 44.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = if (style == 4) Color.White else color, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun ChargingDetailsBlock(style: Int, entries: List<Pair<String, String>>) {
    if (entries.isEmpty()) return
    if (style == 2) {
        Text(entries.joinToString("  |  ") { it.second }, color = Color.White, fontSize = 15.sp, maxLines = 2)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        entries.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowItems.forEach { (name, value) ->
                    val mod = if (style == 1) Modifier.border(BorderStroke(1.dp, Color(0xFF616161)), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp) else Modifier
                    Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, color = Color.White, fontSize = 16.sp, maxLines = 1)
                        Text(name, color = Color(0xFF9E9E9E), fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Settings block shown under the charging screen switches. Every change is saved at once on this phone. */
@Composable
fun ChargingDesignSettings() {
    val c = LocalContext.current
    var s by remember { mutableStateOf(ChargingStyleSettings.load(c)) }
    fun update(n: ChargingStyleSettings) { s = n; ChargingStyleSettings.save(c, n) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Clock style", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChargingStyleSettings.CLOCKS.forEachIndexed { i, n -> FilterChip(selected = s.clock == i, onClick = { update(s.copy(clock = i)) }, label = { Text(n, maxLines = 1) }) }
        }
        Text("Battery gauge style", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChargingStyleSettings.GAUGES.forEachIndexed { i, n -> FilterChip(selected = s.gauge == i, onClick = { update(s.copy(gauge = i)) }, label = { Text(n, maxLines = 1) }) }
        }
        Text("Battery details style", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChargingStyleSettings.DETAILS.forEachIndexed { i, n -> FilterChip(selected = s.details == i, onClick = { update(s.copy(details = i)) }, label = { Text(n, maxLines = 1) }) }
        }
        Text("Show on the charging screen", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        ChargingStyleSettings.ITEMS.forEach { (key, name) ->
            Row(Modifier.fillMaxWidth().clickable { update(s.copy(items = if (key in s.items) s.items - key else s.items + key)) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = key in s.items, onCheckedChange = { on -> update(s.copy(items = if (on) s.items + key else s.items - key)) })
                Text(name, fontSize = 14.sp)
            }
        }
        Text("Gauge colour (Auto follows the battery level)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChargingStyleSettings.COLORS.forEachIndexed { i, (n, col) ->
                val swatch = if (col == Color.Unspecified) Color(0xFF8BC34A) else col
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { update(s.copy(color = i)) }) {
                    Box(Modifier.size(34.dp).background(swatch, CircleShape).border(BorderStroke(if (s.color == i) 3.dp else 1.dp, if (s.color == i) Color.White else Color(0xFF757575)), CircleShape))
                    Text(n, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Text("Gauge brightness: ${(s.gaugeBrightness * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Slider(value = s.gaugeBrightness, onValueChange = { update(s.copy(gaugeBrightness = it.coerceIn(0.3f, 1f))) }, valueRange = 0.3f..1f)
        Text("Screen brightness while it is open: ${if (s.activePercent >= 100) "Phone setting" else s.activePercent.toString() + "%"}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Slider(value = s.activePercent.toFloat(), onValueChange = { update(s.copy(activePercent = it.toInt().coerceIn(5, 100))) }, valueRange = 5f..100f)
        Text("Screen brightness when idle (after 15 seconds): ${s.dimPercent}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Slider(value = s.dimPercent.toFloat(), onValueChange = { update(s.copy(dimPercent = it.toInt().coerceIn(5, 50))) }, valueRange = 5f..50f)
    }
}
