package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Movement-triggered location power saving (opt-in).
 *
 * While the phone is moving, location stays on. When it stops, location is
 * switched off after a short grace period; while still, it gets a brief
 * refresh pulse every few minutes (see GpsPowerPolicy for the exact rules).
 *
 * This writes the secure `location_mode` key, which Android only allows after
 * the user grants WRITE_SECURE_SETTINGS once through ADB (the same method
 * automation apps like MacroDroid use). Without that grant the controller is
 * inert and the UI says so. Movement is read from the accelerometer only -
 * no location permission is needed for detection, and nothing leaves the phone.
 */
object GpsPowerController {

    private const val PREFS = "netra_gps_power"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SAVED_MODE = "saved_mode"
    private const val LOCATION_MODE_OFF = 0
    private const val LOCATION_MODE_HIGH_ACCURACY = 3
    private const val TICK_MS = 15_000L
    private const val MOTION_THRESHOLD = 1.5f
    private const val WINDOW = 12

    enum class GrantState { GRANTED, NOT_GRANTED }

    data class Status(
        val enabled: Boolean,
        val grant: GrantState,
        val locationOn: Boolean?,
        val moving: Boolean,
    )

    private val policy = GpsPowerPolicy()
    private var state: GpsPowerPolicy.State? = null
    private var job: Job? = null
    private var sensorManager: SensorManager? = null
    private val window = FloatArray(WINDOW)
    private var windowCount = 0
    private var windowPos = 0
    @Volatile private var movingNow = false
    @Volatile private var lastKnownLocationOn: Boolean? = null

    private val _status = MutableStateFlow(Status(false, GrantState.NOT_GRANTED, null, false))
    val status: StateFlow<Status> = _status

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val mag = sqrt(
                event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2],
            )
            window[windowPos] = mag
            windowPos = (windowPos + 1) % WINDOW
            if (windowCount < WINDOW) windowCount++
            if (windowCount >= 4) {
                var mean = 0f
                for (i in 0 until windowCount) mean += window[i]
                mean /= windowCount
                var varSum = 0f
                for (i in 0 until windowCount) {
                    val d = window[i] - mean
                    varSum += d * d
                }
                movingNow = sqrt(varSum / windowCount) > MOTION_THRESHOLD
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
    }

    fun hasGrant(c: Context): Boolean =
        c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun isEnabled(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /** User switch. Enabling without the ADB grant reports NOT_GRANTED and changes nothing. */
    @Synchronized
    fun setEnabled(c: Context, on: Boolean): GrantState {
        val app = c.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on && !hasGrant(app)) {
            publish(app)
            return GrantState.NOT_GRANTED
        }
        if (on) start(app) else stop(app)
        publish(app)
        return if (hasGrant(app)) GrantState.GRANTED else GrantState.NOT_GRANTED
    }

    /** Called by BatteryMonitorService on start so an opted-in user keeps the feature across restarts. */
    @Synchronized
    fun ensureRunning(c: Context) {
        if (isEnabled(c) && hasGrant(c) && job == null) start(c.applicationContext)
        publish(c.applicationContext)
    }

    @Synchronized
    private fun start(c: Context) {
        if (job != null) return
        state = policy.initialState(System.currentTimeMillis())
        sensorManager = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        job = scope.launch {
            while (isActive) {
                step(c)
                delay(TICK_MS)
            }
        }
    }

    @Synchronized
    private fun stop(c: Context) {
        job?.cancel()
        job = null
        sensorManager?.unregisterListener(listener)
        sensorManager = null
        state = null
        movingNow = false
        // Hand control back honestly: restore the mode the phone had before we last switched it off.
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_SAVED_MODE) && hasGrant(c)) {
            writeMode(c, prefs.getInt(KEY_SAVED_MODE, LOCATION_MODE_HIGH_ACCURACY))
            prefs.edit().remove(KEY_SAVED_MODE).apply()
        }
    }

    private fun step(c: Context) {
        val now = System.currentTimeMillis()
        val prev = state ?: policy.initialState(now)
        val (next, wantOn) = policy.tick(prev, now, movingNow)
        state = next
        val currentlyOn = readLocationMode(c) != LOCATION_MODE_OFF
        if (wantOn != currentlyOn) {
            if (!wantOn) {
                // Save the user's mode once, before our first switch-off.
                val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (!prefs.contains(KEY_SAVED_MODE)) {
                    prefs.edit().putInt(KEY_SAVED_MODE, readLocationMode(c)).apply()
                }
                writeMode(c, LOCATION_MODE_OFF)
            } else {
                val restore = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_SAVED_MODE, LOCATION_MODE_HIGH_ACCURACY)
                writeMode(c, restore)
            }
        }
        lastKnownLocationOn = wantOn
        publish(c)
    }

    private fun readLocationMode(c: Context): Int = try {
        Settings.Secure.getInt(c.contentResolver, "location_mode", LOCATION_MODE_HIGH_ACCURACY)
    } catch (e: Exception) {
        LOCATION_MODE_HIGH_ACCURACY
    }

    private fun writeMode(c: Context, mode: Int) {
        try {
            Settings.Secure.putInt(c.contentResolver, "location_mode", mode)
        } catch (e: SecurityException) {
            Log.w("GpsPowerController", "Grant lost; controller stops changing location", e)
        }
    }

    private fun publish(c: Context) {
        _status.value = Status(
            enabled = isEnabled(c),
            grant = if (hasGrant(c)) GrantState.GRANTED else GrantState.NOT_GRANTED,
            locationOn = lastKnownLocationOn,
            moving = movingNow,
        )
    }
}
