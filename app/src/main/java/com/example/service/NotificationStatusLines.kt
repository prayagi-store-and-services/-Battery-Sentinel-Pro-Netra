package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.RemoteViews
import com.example.R
import com.example.model.NetraCentralState
import java.util.Locale

/** Static cached pixels. Values come from the existing central feed, never a sampler/animation.
 * Numeric health is deliberately absent: Android's GOOD status is not a capacity percentage.
 */
internal object NotificationStatusLines {
    data class Values(val battery: Int?, val temp: Float?, val fresh: Boolean) {
        val text: String get() = if (!fresh) "Battery Unavailable\nTemperature Unavailable\nHealth Unavailable"
            else "Battery ${battery?.let { "$it%" } ?: "Unavailable"}\n" +
                "Temperature ${temp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unavailable"}\nHealth Unavailable"
    }
    fun values(s: NetraCentralState) = Values(
        s.batteryLevel?.takeIf { s.isDataFresh && it in 0..100 },
        s.temperatureCelsius?.takeIf { s.isDataFresh && it.isFinite() && it in -50f..150f }, s.isDataFresh)

    private var cachedKey: Values? = null
    private var cached: Bitmap? = null
    @Synchronized fun bitmap(v: Values): Bitmap {
        if (cachedKey == v) cached?.let { return it }
        val image = Bitmap.createBitmap(216, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
        val input = listOf(v.battery?.toFloat(), v.temp, null)
        input.forEachIndexed { index, value ->
            val y = 16f + index * 32f
            paint.color = Color.rgb(125, 135, 148)
            paint.alpha = 100
            canvas.drawLine(5f, y, 211f, y, paint)
            if (value != null && v.fresh && value > 0f) {
                paint.alpha = 255
                paint.color = color(value, index == 1)
                canvas.drawLine(5f, y, 5f + 206f * value.coerceIn(0f,100f) / 100f, y, paint)
            }
        }
        cachedKey = v; cached = image
        return image
    }
    fun color(value: Float, thermal: Boolean): Int = when {
        thermal && value >= 39.5f -> Color.rgb(240,68,82)
        thermal && value >= 38f -> Color.rgb(244,186,61)
        thermal -> Color.rgb(53,200,144)
        value <= 15f -> Color.rgb(240,68,82)
        value <= 30f -> Color.rgb(244,186,61)
        else -> Color.rgb(53,200,144)
    }
    fun views(c: Context, s: NetraCentralState, line: String, expanded: Boolean): RemoteViews {
        val v = values(s)
        return RemoteViews(c.packageName, if (expanded) R.layout.notification_lines_expanded else R.layout.notification_lines_compact).apply {
            setImageViewBitmap(R.id.status_lines, bitmap(v))
            setContentDescription(R.id.status_lines, "Top battery, middle temperature, bottom health. ${v.text}")
            setTextViewText(R.id.status_values, if (expanded) v.text else
                "${v.battery?.let { "$it%" } ?: "Battery Unavailable"} · ${v.temp?.let { String.format(Locale.US,"%.1f°C",it) } ?: "Temperature Unavailable"}\nHealth Unavailable")
            setContentDescription(R.id.status_values, v.text + ". " + line)
            if (expanded) setTextViewText(R.id.status_detail, line)
        }
    }
}
