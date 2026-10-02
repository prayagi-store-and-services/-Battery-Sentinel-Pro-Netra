package com.example.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.model.LiveChargingSample
import com.example.model.LiveChargingSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Abstraction for battery electrical hardware readings to support unit testing and real device execution.
 */
interface BatteryHardwareProvider {
    fun isCharging(): Boolean
    fun getPluggedType(): String
    fun getVoltageMv(): Int?
    fun getCurrentMicroAmps(): Int?
    fun getBatteryLevelPercent(): Float?
}

/**
 * Native Android implementation reading instantaneous BatteryManager properties and sticky broadcast.
 */
class AndroidBatteryHardwareProvider(private val context: Context) : BatteryHardwareProvider {
    private val speedEngine = ChargingSpeedEngine()

    private fun getStickyBatteryIntent(): Intent? {
        return try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
    }

    override fun isCharging(): Boolean {
        val intent = getStickyBatteryIntent() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (plugged > 0 && status != BatteryManager.BATTERY_STATUS_DISCHARGING && status != BatteryManager.BATTERY_STATUS_NOT_CHARGING)
    }

    override fun getPluggedType(): String {
        val intent = getStickyBatteryIntent() ?: return "UNPLUGGED"
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> if (plugged > 0) "CHARGER" else "UNPLUGGED"
        }
    }

    override fun getVoltageMv(): Int? {
        val intent = getStickyBatteryIntent() ?: return null
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        return if (voltage > 0) voltage else null
    }

    override fun getCurrentMicroAmps(): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val raw = try {
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        } catch (_: Exception) {
            Int.MIN_VALUE
        }
        return if (raw != Int.MIN_VALUE) raw else null
    }

    override fun getBatteryLevelPercent(): Float? {
        val intent = getStickyBatteryIntent() ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) {
            (level.toFloat() * 100f) / scale.toFloat()
        } else null
    }
}

/**
 * Charging-session-only live electrical monitoring engine.
 * Refreshes approximately once per second while the monitor screen is active and charging is confirmed.
 * Stops immediately when charging ends or the user leaves the screen.
 */
class LiveChargingSessionEngine(
    private val hardwareProvider: BatteryHardwareProvider,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val pollingIntervalMs: Long = 1000L
) {
    private val mutex = Mutex()
    private var refreshJob: Job? = null
    private var isScreenActive = false
    private val speedEngine = ChargingSpeedEngine()
    private val rollingHistoryBuffer = ArrayDeque<LiveChargingSample>(300)

    private val _sessionState = MutableStateFlow(LiveChargingSessionState())
    val sessionState: StateFlow<LiveChargingSessionState> = _sessionState.asStateFlow()

    /**
     * Called when the Live Charging Monitor screen enters the foreground.
     */
    fun onScreenResumed() {
        scope.launch {
            mutex.withLock {
                isScreenActive = true
                val currentlyCharging = hardwareProvider.isCharging()
                if (currentlyCharging) {
                    if (!_sessionState.value.isChargingActive) {
                        startNewSessionLocked()
                    }
                    ensurePollingLoopRunningLocked()
                    sampleOnceLocked()
                } else {
                    stopPollingLoopLocked()
                    _sessionState.value = _sessionState.value.copy(
                        isChargingActive = false,
                        isDischarging = true,
                        currentBatteryPercent = hardwareProvider.getBatteryLevelPercent()
                    )
                }
            }
        }
    }

    /**
     * Called when the user leaves the Live Charging Monitor screen.
     * Stops the 1-second polling loop immediately to conserve resources.
     */
    fun onScreenPaused() {
        scope.launch {
            mutex.withLock {
                isScreenActive = false
                stopPollingLoopLocked()
            }
        }
    }

    /**
     * Central Unit or broadcast receiver callback indicating battery/charger status changed.
     */
    fun onBatteryStateChanged(isCharging: Boolean, status: Int, plugged: Int) {
        scope.launch {
            mutex.withLock {
                val isConfirmedCharging = isCharging && (status == BatteryManager.BATTERY_STATUS_CHARGING || plugged > 0) &&
                    status != BatteryManager.BATTERY_STATUS_DISCHARGING &&
                    status != BatteryManager.BATTERY_STATUS_NOT_CHARGING
                if (isConfirmedCharging) {
                    if (!_sessionState.value.isChargingActive) {
                        startNewSessionLocked()
                    }
                    if (isScreenActive) {
                        ensurePollingLoopRunningLocked()
                        sampleOnceLocked()
                    }
                } else {
                    // Charging stopped or disconnected -> stop immediately!
                    stopPollingLoopLocked()
                    _sessionState.value = _sessionState.value.copy(
                        isChargingActive = false,
                        isDischarging = true,
                        currentPowerWatts = null,
                        currentCurrentA = null,
                        pluggedSource = if (plugged > 0) "IDLE" else "UNPLUGGED",
                        currentBatteryPercent = hardwareProvider.getBatteryLevelPercent()
                    )
                }
            }
        }
    }

    private fun startNewSessionLocked() {
        val now = clock()
        val currentLevel = hardwareProvider.getBatteryLevelPercent()
        val plugged = hardwareProvider.getPluggedType()

        _sessionState.value = LiveChargingSessionState(
            isChargingActive = true,
            sessionStartTimeMs = now,
            sessionDurationSeconds = 0L,
            currentBatteryPercent = currentLevel,
            pluggedSource = plugged,
            lastUpdatedTimeMs = now,
            rollingHistory = rollingHistoryBuffer.toList(),
            isDischarging = false
        )
    }

    private fun ensurePollingLoopRunningLocked() {
        if (refreshJob?.isActive == true) return

        refreshJob = scope.launch {
            while (isActive) {
                delay(pollingIntervalMs)
                mutex.withLock {
                    if (!isScreenActive || !hardwareProvider.isCharging()) {
                        stopPollingLoopLocked()
                        _sessionState.value = _sessionState.value.copy(
                            isChargingActive = false,
                            isDischarging = !hardwareProvider.isCharging()
                        )
                        return@launch
                    }
                    sampleOnceLocked()
                }
            }
        }
    }

    private fun stopPollingLoopLocked() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * Executes one electrical measurement sample and updates the state.
     */
    fun sampleOnce() {
        scope.launch {
            mutex.withLock {
                sampleOnceLocked()
            }
        }
    }

    private fun sampleOnceLocked() {
        val now = clock()
        val isCharging = hardwareProvider.isCharging()
        val plugged = hardwareProvider.getPluggedType()
        val levelPercent = hardwareProvider.getBatteryLevelPercent()
        val voltageMv = hardwareProvider.getVoltageMv()
        val rawCurrentMicroAmps = hardwareProvider.getCurrentMicroAmps()

        val startTime = _sessionState.value.sessionStartTimeMs ?: now
        val durationSeconds = (now - startTime).coerceAtLeast(0L) / 1000L

        if (!isCharging) {
            _sessionState.value = _sessionState.value.copy(
                isChargingActive = false,
                isDischarging = true,
                currentBatteryPercent = levelPercent,
                pluggedSource = plugged,
                currentPowerWatts = null,
                currentCurrentA = null
            )
            stopPollingLoopLocked()
            return
        }

        // Validate voltage within plausible hardware ranges (2.0V - 15.0V)
        val voltageV = voltageMv?.let {
            val v = it / 1000.0f
            if (v in 2.0f..15.0f) v else null
        }

        // Normalize current based on device fuel-gauge unit conventions
        val currentMa = rawCurrentMicroAmps?.let { raw ->
            if (raw == Int.MIN_VALUE) null
            else speedEngine.normalizeToMilliAmps(raw, activelyCharging = true)
        }
        val currentA = currentMa?.let {
            val a = abs(it) / 1000.0f
            if (a in 0.0f..30.0f) a else null
        }

        // Calculate power strictly from compatible valid voltage and current
        val powerWatts = if (voltageV != null && currentA != null) {
            voltageV * currentA
        } else {
            null
        }

        val sample = LiveChargingSample(
            timestamp = now,
            voltageV = voltageV,
            currentA = currentA,
            powerWatts = powerWatts,
            batteryPercent = levelPercent,
            pluggedSource = plugged,
            isValid = voltageV != null || currentA != null || levelPercent != null
        )

        if (rollingHistoryBuffer.size >= 300) {
            rollingHistoryBuffer.removeFirst()
        }
        rollingHistoryBuffer.addLast(sample)

        _sessionState.value = _sessionState.value.copy(
            isChargingActive = true,
            sessionStartTimeMs = startTime,
            sessionDurationSeconds = durationSeconds,
            currentVoltageV = voltageV,
            currentCurrentA = currentA,
            currentPowerWatts = powerWatts,
            currentBatteryPercent = levelPercent,
            pluggedSource = plugged,
            lastUpdatedTimeMs = now,
            rollingHistory = rollingHistoryBuffer.toList(),
            isDischarging = false
        )
    }

    /**
     * Checks if the 1-second refresh job is currently active.
     */
    fun isPollingActive(): Boolean = refreshJob?.isActive == true

    /**
     * Checks if the monitor screen is recorded as active.
     */
    fun isScreenActiveForTesting(): Boolean = isScreenActive

    /**
     * For unit tests: resets session and history.
     */
    fun resetForTests() {
        stopPollingLoopLocked()
        rollingHistoryBuffer.clear()
        _sessionState.value = LiveChargingSessionState()
        isScreenActive = false
    }
}
