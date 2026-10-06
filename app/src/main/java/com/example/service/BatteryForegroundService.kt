package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Foreground Service that continuously tracks battery percentage, temperature,
 * and charging status in the background using the Android BatteryManager system API.
 *
 * Implements an event-driven ultra-low power architecture by responding to system
 * battery state broadcasts without busy polling loops.
 */
class BatteryForegroundService : Service() {

    private var isReceiverRegistered = false
    private var batteryManager: BatteryManager? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action
            if (action == Intent.ACTION_BATTERY_CHANGED ||
                action == Intent.ACTION_POWER_CONNECTED ||
                action == Intent.ACTION_POWER_DISCONNECTED
            ) {
                updateBatteryStateFromIntent(intent)
            }
            if (action == Intent.ACTION_POWER_CONNECTED) { openChargingScreenIfAllowed(wake = true) }
            if (action == Intent.ACTION_SCREEN_OFF) {
                // Screen turned off (idle timeout or lock) while the charger is connected: bring the charging screen back. It stays dark and shows when the phone is next woken.
                val sticky = try { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Exception) { null }
                if ((sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0) {
                    openChargingScreenIfAllowed(wake = false)
                }
            }
        }
    }

    /** Charging screen from the background: only when the user turned it on AND Android's Display over other apps is granted. */
    private fun openChargingScreenIfAllowed(wake: Boolean) {
        try {
            val on = getSharedPreferences(com.example.ui.components.CHARGING_SCREEN_PREFS, Context.MODE_PRIVATE)
                .getBoolean(com.example.ui.components.KEY_BACKGROUND, true)
            if (on && android.provider.Settings.canDrawOverlays(this)) {
                startActivity(Intent(this, com.example.ui.ChargingScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(com.example.ui.ChargingScreenActivity.EXTRA_WAKE, wake))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the charging screen", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        createNotificationChannel()

        // Read initial state immediately from sticky broadcast and BatteryManager API
        val initialIntent = try {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            Log.w(TAG, "Could not obtain sticky battery intent", e)
            null
        }

        val initialState = if (initialIntent != null) {
            parseBatteryData(initialIntent, batteryManager)
        } else {
            queryBatteryManagerDirectly(batteryManager)
        }.copy(isTrackingActive = true)

        _batteryStateFlow.value = initialState

        val notification = buildTrackingNotification(this, initialState)
        startForeground(NOTIFICATION_ID, notification)

        registerBatteryBroadcastReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        // Re-verify sticky intent on start command
        try {
            val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (sticky != null) {
                updateBatteryStateFromIntent(sticky)
            }
        } catch (_: Exception) {}

        return START_STICKY
    }

    private fun registerBatteryBroadcastReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(batteryReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register battery receiver", e)
            }
        }
    }

    private fun updateBatteryStateFromIntent(intent: Intent) {
        val newState = parseBatteryData(intent, batteryManager).copy(isTrackingActive = true)
        _batteryStateFlow.value = newState
        updateNotification(newState)
    }

    private fun updateNotification(state: BatteryTrackState) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildTrackingNotification(this, state))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update foreground notification", e)
        }
    }

    private fun stopForegroundService() {
        _batteryStateFlow.value = _batteryStateFlow.value.copy(isTrackingActive = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering battery receiver", e)
            }
            isReceiverRegistered = false
        }
        _batteryStateFlow.value = _batteryStateFlow.value.copy(isTrackingActive = false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Background Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors real-time battery percentage, temperature, and charging status in background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "BatteryForegroundService"
        const val CHANNEL_ID = "battery_foreground_tracker_channel"
        const val NOTIFICATION_ID = 4001
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_BATTERY_TRACKER"

        private val _batteryStateFlow = MutableStateFlow(BatteryTrackState())
        val batteryState: StateFlow<BatteryTrackState> = _batteryStateFlow.asStateFlow()

        /**
         * Parses battery percentage, temperature, and charging status from an ACTION_BATTERY_CHANGED intent
         * and the BatteryManager system API.
         */
        fun parseBatteryData(intent: Intent, batteryManager: BatteryManager?): BatteryTrackState {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

            val percentage = if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
            } else {
                val directCapacity = try {
                    batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                } catch (_: Exception) { -1 }
                if (directCapacity in 0..100) directCapacity else 0
            }

            // EXTRA_TEMPERATURE is in tenths of a degree Celsius (e.g. 285 = 28.5°C)
            val rawTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val temperatureCelsius = rawTemperature / 10.0f

            val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val isChargingDirect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    batteryManager?.isCharging == true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
            val isCharging = isChargingDirect || statusInt == BatteryManager.BATTERY_STATUS_CHARGING

            val statusLabel = when (statusInt) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> if (isCharging) "Charging" else "Unknown"
            }

            val pluggedInt = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val pluggedSource = when (pluggedInt) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                4 -> "Dock"
                else -> if (isCharging) "Connected" else "Unplugged"
            }

            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

            val currentMicroAmps = try {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            } catch (_: Exception) {
                0
            }

            return BatteryTrackState(
                percentage = percentage,
                temperatureCelsius = temperatureCelsius,
                isCharging = isCharging,
                status = statusLabel,
                pluggedSource = pluggedSource,
                voltageMv = voltageMv,
                currentMicroAmps = currentMicroAmps,
                timestamp = System.currentTimeMillis()
            )
        }

        /**
         * Direct query fallback using BatteryManager system service properties.
         */
        fun queryBatteryManagerDirectly(batteryManager: BatteryManager?): BatteryTrackState {
            val capacity = try {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
            } catch (_: Exception) { 0 }

            val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    batteryManager?.isCharging == true
                } catch (_: Exception) { false }
            } else false

            val currentMicroAmps = try {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            } catch (_: Exception) { 0 }

            return BatteryTrackState(
                percentage = capacity.coerceIn(0, 100),
                temperatureCelsius = 0f,
                isCharging = isCharging,
                status = if (isCharging) "Charging" else "On Battery",
                pluggedSource = if (isCharging) "Connected" else "Unplugged",
                voltageMv = 0,
                currentMicroAmps = currentMicroAmps,
                timestamp = System.currentTimeMillis()
            )
        }

        /**
         * Builds the ongoing foreground notification displaying real-time
         * battery percentage, temperature, and charging status.
         */
        fun buildTrackingNotification(context: Context, state: BatteryTrackState): Notification {
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val statusText = if (state.isCharging) {
                "${state.status} (${state.pluggedSource})"
            } else {
                state.status
            }

            val title = "Battery: ${state.percentage}% • $statusText"
            val tempFormatted = String.format(Locale.US, "%.1f°C", state.temperatureCelsius)
            val subtext = "Temperature: $tempFormatted • $statusText"

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(subtext)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Battery Level: ${state.percentage}%\nTemperature: $tempFormatted\nCharging Status: $statusText"))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()
        }

        /**
         * Starts the BatteryForegroundService.
         */
        fun startService(context: Context) {
            val intent = Intent(context, BatteryForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BatteryForegroundService", e)
            }
        }

        /**
         * Stops the BatteryForegroundService.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, BatteryForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop BatteryForegroundService", e)
            }
        }

        fun isRunning(): Boolean = _batteryStateFlow.value.isTrackingActive
    }
}

/**
 * Telemetry model emitted by [BatteryForegroundService].
 */
data class BatteryTrackState(
    val percentage: Int = 0,
    val temperatureCelsius: Float = 0f,
    val isCharging: Boolean = false,
    val status: String = "Unknown",
    val pluggedSource: String = "Unplugged",
    val voltageMv: Int = 0,
    val currentMicroAmps: Int = 0,
    val timestamp: Long = 0L,
    val isTrackingActive: Boolean = false
)
