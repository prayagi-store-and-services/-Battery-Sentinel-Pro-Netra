package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draw helpers for the opt-in gauge animations. Every effect stays inside its ring band of the
 * dot gauge: spark at the outer band, temperature effects in the middle band, heartbeat in the
 * inner band. Callers draw only from real readings and the trend from TrendTracker; nothing here
 * fabricates a state.
 */

/** Charging spark: a small bolt at the top of the outer ring band, gently pulsing. */
internal fun DrawScope.drawSpark(center: Offset, unit: Float, tick: Long) {
    val pulse = (sin(tick / 3f) + 1f) / 2f
    val top = center + Offset(0f, -unit * .425f)
    val s = unit * .05f
    val path = Path().apply {
        moveTo(top.x + s * .15f, top.y - s)
        lineTo(top.x - s * .45f, top.y + s * .1f)
        lineTo(top.x - s * .05f, top.y + s * .1f)
        lineTo(top.x - s * .15f, top.y + s)
        lineTo(top.x + s * .45f, top.y - s * .1f)
        lineTo(top.x + s * .05f, top.y - s * .1f)
        close()
    }
    drawPath(path, Color(0xFFFACC15).copy(alpha = .45f + .55f * pulse))
}

/** Temperature rising: three flickering flames at the top of the middle band. */
internal fun DrawScope.drawFlames(center: Offset, unit: Float, tick: Long) {
    val base = center + Offset(0f, -unit * .355f)
    for (i in 0..2) {
        val w = unit * .028f
        val h = unit * (.045f + .012f * sin(tick / 2f + i * 1.7f))
        val x = base.x + (i - 1) * unit * .055f
        val path = Path().apply {
            moveTo(x - w, base.y + h * .4f)
            quadraticBezierTo(x - w, base.y - h * .6f, x, base.y - h)
            quadraticBezierTo(x + w, base.y - h * .6f, x + w, base.y + h * .4f)
            quadraticBezierTo(x, base.y + h * .9f, x - w, base.y + h * .4f)
            close()
        }
        drawPath(path, if (i == 1) Color(0xFFF97316) else Color(0xFFEF4444))
    }
}

/** Temperature falling: a liquid wave along the bottom of the middle band. */
internal fun DrawScope.drawWave(center: Offset, unit: Float, tick: Long) {
    val base = center + Offset(0f, unit * .355f)
    val w = unit * .11f
    val path = Path()
    for (i in 0..24) {
        val x = base.x - w + (2 * w) * i / 24f
        val y = base.y + unit * .018f * sin(tick / 2.5f + i / 2.2f)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Color(0xFF60A5FA), style = Stroke(width = unit * .012f))
}

/** Health heartbeat: an ECG trace with a pulse dot in the inner band. Weakening trend slows and fades it. */
internal fun DrawScope.drawEcg(center: Offset, unit: Float, tick: Long, weakening: Boolean) {
    val base = center + Offset(0f, unit * .285f)
    val speed = if (weakening) 2f else 5f
    val shift = (tick * speed) % 90f
    val fade = if (weakening) (.55f + .35f * sin(tick / 6f)) else 1f
    val amp = if (weakening) .55f else 1f
    val w = unit * .12f
    val path = Path()
    for (i in 0..28) {
        val x = base.x - w + (2 * w) * i / 28f
        val p = (i * 7f + shift) % 90f
        var yy = base.y
        if (p in 30f..36f) yy = base.y - unit * .010f * amp
        else if (p in 36f..42f) yy = base.y + unit * .030f * amp
        else if (p in 42f..48f) yy = base.y - unit * .040f * amp
        else if (p in 48f..54f) yy = base.y + unit * .010f * amp
        if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
    }
    drawPath(path, Color(0xFF34D399).copy(alpha = fade.coerceIn(0f, 1f)), style = Stroke(width = unit * .010f))
    val pr = unit * (.020f + (if (weakening) .004f else .008f) * sin(tick / (if (weakening) 4f else 2f)))
    drawCircle(Color(0xFFE8546C).copy(alpha = fade.coerceIn(0f, 1f)), pr, base + Offset(0f, -unit * .045f))
}
