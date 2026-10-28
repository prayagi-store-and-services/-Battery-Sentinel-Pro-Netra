package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.Brand
import com.example.R
import com.example.model.NetraCentralState
import java.util.Locale

/**
 * The ONE ongoing status notification for the whole app. Both foreground services
 * (BatteryMonitorService and BatteryForegroundService) post with the same id and channel
 * and build from the same central state, so Android shows a single status line instead
 * of two competing notifications. Alert notifications (target, overheat, threshold) keep
 * their own channel and ids and are unchanged.
 */
object SentinelStatusNotification {

    const val CHANNEL_ID = "netra_service_channel"
    const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Netra Battery Sentinel Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "24/7 ultra-low power real-time battery & thermal telemetry status"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildPaused(context: Context): Notification {
        val pendingIntent = PendingIntent.getActivity(context, 0, Brand.launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = "Monitoring paused after repeated crashes. Open app to retry. Thermal alerts may be unavailable."
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(Brand.name)
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).setContentIntent(pendingIntent).build()
    }

    fun build(context: Context, state: NetraCentralState): Notification {
        val intent = Brand.launchIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mode = when {
            state.isCharging == true -> StatusLineMode.CHARGING
            state.isCharging == false && state.isChargerConnected == false -> StatusLineMode.ON_BATTERY
            state.isCharging == false && state.isChargerConnected == true -> StatusLineMode.CONNECTED_NOT_CHARGING
            else -> StatusLineMode.UNKNOWN
        }
        val etaMinutes = when (mode) {
            StatusLineMode.CHARGING -> state.chargingEtaMinutes
            StatusLineMode.ON_BATTERY -> state.dischargingEtaMinutes
            else -> null
        }
        val etaConfident = when (mode) {
            StatusLineMode.CHARGING -> state.chargingEtaConfident
            StatusLineMode.ON_BATTERY -> state.dischargingEtaConfident
            else -> false
        }
        val line = buildLine(
            level = state.batteryLevel,
            tempC = state.temperatureCelsius,
            mode = mode,
            etaMinutes = etaMinutes,
            etaConfident = etaConfident,
            dataFresh = state.isDataFresh
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(Brand.name)
            .setContentText(line)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(NotificationStatusLines.views(context, state, line, false))
            .setCustomBigContentView(NotificationStatusLines.views(context, state, line, true))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    enum class StatusLineMode { CHARGING, ON_BATTERY, CONNECTED_NOT_CHARGING, UNKNOWN }

    /**
     * The single status line: '<pct>% - <temp>C - ~<eta> to full' while charging,
     * '~<eta> remaining' on battery. An estimate is shown only when the learned curve is
     * confident; until then the line says so honestly. Stale data is labelled, never hidden.
     */
    fun buildLine(
        level: Int?,
        tempC: Float?,
        mode: StatusLineMode,
        etaMinutes: Int?,
        etaConfident: Boolean,
        dataFresh: Boolean = true
    ): String {
        if (!dataFresh) return "Last-known battery data - live update unavailable"
        val parts = mutableListOf<String>()
        parts.add(level?.let { "$it%" } ?: "Battery")
        tempC?.let { parts.add(String.format(Locale.US, "%.1f°C", it)) }
        when (mode) {
            StatusLineMode.CHARGING -> parts.add(
                if (etaMinutes != null && etaConfident) "~${formatMinutes(etaMinutes)} to full"
                else "learning your phone's pattern"
            )
            StatusLineMode.ON_BATTERY -> parts.add(
                if (etaMinutes != null && etaConfident) "~${formatMinutes(etaMinutes)} remaining"
                else "learning your phone's pattern"
            )
            StatusLineMode.CONNECTED_NOT_CHARGING -> parts.add("connected, not charging")
            StatusLineMode.UNKNOWN -> { }
        }
        return parts.joinToString(" - ")
    }

    private fun formatMinutes(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
}
