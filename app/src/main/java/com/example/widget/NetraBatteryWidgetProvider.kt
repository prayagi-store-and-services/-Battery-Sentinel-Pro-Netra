package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.NetraApplication
import com.example.R
import com.example.model.BatteryTelemetry
import com.example.service.BatteryMonitorService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The single Netra home screen widget: charging state, level, temperature, voltage,
 * current, power, health and time estimate at a glance.
 *
 * Truth rule: every value comes from the live battery telemetry. A value the phone does
 * not report is shown as "Unavailable". The widget is redrawn only when the monitor
 * service publishes a reading (no polling, no alarms, no extra background work).
 */
class NetraBatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val telemetry = BatteryMonitorService.liveTelemetryFlow.value
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId, telemetry)
        }
    }

    /** Text shown on the widget. Pure data so it can be unit tested without a phone. */
    data class WidgetModel(
        val percent: String,
        val status: String,
        val source: String,
        val temperature: String,
        val voltage: String,
        val current: String,
        val power: String,
        val health: String,
        val etaLabel: String,
        val eta: String,
        val updated: String,
        val charging: Boolean
    )

    companion object {
        private const val UNAVAILABLE = "Unavailable"
        private const val STALE_AFTER_MS = 15 * 60 * 1000L

        fun buildModel(t: BatteryTelemetry, nowMs: Long): WidgetModel {
            if (!t.isDataAvailable) {
                return WidgetModel(
                    UNAVAILABLE, UNAVAILABLE, "", UNAVAILABLE, UNAVAILABLE, UNAVAILABLE,
                    UNAVAILABLE, UNAVAILABLE, "Time left", UNAVAILABLE, "No reading yet", false
                )
            }
            val source = if (t.isCharging) {
                val plug = t.pluggedType.takeIf { it != "UNKNOWN" && it != "BATTERY" }
                val speed = t.chargingSpeedLabel.takeIf { it.isNotBlank() && it != UNAVAILABLE }
                listOfNotNull(plug, speed).joinToString(" - ")
            } else {
                "On battery"
            }
            val eta: String
            val etaLabel: String
            if (t.isCharging) {
                etaLabel = "Full in"
                eta = t.timeToFullMinutes?.takeIf { it > 0 }?.let { formatMinutes(it) } ?: UNAVAILABLE
            } else {
                etaLabel = "Time left"
                eta = t.estimatedDischargeHours?.takeIf { it > 0f }
                    ?.let { formatMinutes((it * 60f).toInt()) } ?: UNAVAILABLE
            }
            val clock = if (t.lastUpdateTimestamp > 0L) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.lastUpdateTimestamp))
            } else null
            val updated = when {
                clock == null -> "No timestamp"
                nowMs - t.lastUpdateTimestamp > STALE_AFTER_MS -> "Stale, last reading $clock"
                else -> "Updated $clock"
            }
            return WidgetModel(
                percent = "${t.level}%",
                status = if (t.isCharging) "Charging" else "Discharging",
                source = source,
                temperature = if (t.temperature > 0f) String.format(Locale.US, "%.1f\u00B0C", t.temperature) else UNAVAILABLE,
                voltage = if (t.voltageMv > 0) String.format(Locale.US, "%.2f V", t.voltageMv / 1000f) else UNAVAILABLE,
                current = if (t.currentMa != 0) "${abs(t.currentMa)} mA" else UNAVAILABLE,
                power = if (t.powerWatts > 0f) String.format(Locale.US, "%.1f W", t.powerWatts) else UNAVAILABLE,
                health = t.healthString.takeIf { it.isNotBlank() } ?: UNAVAILABLE,
                etaLabel = etaLabel,
                eta = eta,
                updated = updated,
                charging = t.isCharging
            )
        }

        private fun formatMinutes(total: Int): String {
            val h = total / 60
            val m = total % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }

        fun getThemeColorHex(themeName: String): String {
            return when (themeName.uppercase()) {
                "EMERALD" -> "#00E676"
                "AMBER" -> "#FFB300"
                "RED" -> "#FF5252"
                "PURPLE" -> "#B388FF"
                "MONO" -> "#FFFFFF"
                else -> "#00E5FF" // CYAN
            }
        }

        fun getBgColorHex(bgStyle: String): String {
            return when (bgStyle.uppercase()) {
                "AMOLED_BLACK" -> "#000000"
                "TRANSLUCENT" -> "#0D1520"
                else -> "#101926" // GLASS_DARK
            }
        }

        fun updateAllWidgets(context: Context, telemetry: BatteryTelemetry) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, NetraBatteryWidgetProvider::class.java)
            for (widgetId in appWidgetManager.getAppWidgetIds(componentName)) {
                updateAppWidget(context, appWidgetManager, widgetId, telemetry)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            telemetry: BatteryTelemetry
        ) {
            val settings = try {
                NetraApplication.instance.settingsRepository.settings.value
            } catch (_: Exception) {
                null
            }
            val accent = Color.parseColor(getThemeColorHex(settings?.widgetThemeColor ?: "CYAN"))
            val bg = Color.parseColor(getBgColorHex(settings?.widgetBackgroundStyle ?: "GLASS_DARK"))
            val m = buildModel(telemetry, System.currentTimeMillis())

            val views = RemoteViews(context.packageName, R.layout.widget_netra_battery)
            views.setInt(R.id.widget_root, "setBackgroundColor", bg)
            views.setTextViewText(R.id.widget_battery_percent, m.percent)
            views.setTextColor(R.id.widget_battery_percent, accent)
            views.setTextViewText(R.id.widget_battery_status, m.status)
            views.setTextColor(R.id.widget_battery_status, if (m.charging) accent else Color.parseColor("#00E676"))
            views.setTextViewText(R.id.widget_source, m.source)
            views.setTextViewText(R.id.widget_temp, m.temperature)
            views.setTextViewText(R.id.widget_voltage, m.voltage)
            views.setTextViewText(R.id.widget_current, m.current)
            views.setTextViewText(R.id.widget_power, m.power)
            views.setTextViewText(R.id.widget_health, m.health)
            views.setTextViewText(R.id.widget_eta_label, m.etaLabel)
            views.setTextViewText(R.id.widget_eta, m.eta)
            views.setTextViewText(R.id.widget_updated, m.updated)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
