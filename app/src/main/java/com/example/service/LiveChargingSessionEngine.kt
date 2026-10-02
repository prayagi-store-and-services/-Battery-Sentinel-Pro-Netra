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
import java.util.Locale
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
    fun getTemperatureCelsius(): Float?
    fun isBatteryFull(): Boolean
    fun computeChargeTimeRemainingMillis(): Long? = null
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

    override fun getTemperatureCelsius(): Float? {
        val intent = getStickyBatteryIntent() ?: return null
        val raw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        return if (raw > 0) raw / 10.0f else null
    }

    override fun isBatteryFull(): Boolean {
        val intent = getStickyBatteryIntent() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return status == BatteryManager.BATTERY_STATUS_FULL || (level > 0 && scale > 0 && level >= scale)
    }

    override fun computeChargeTimeRemainingMillis(): Long? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val remaining = try {
                bm?.computeChargeTimeRemaining() ?: -1L
            } catch (_: Exception) {
                -1L
            }
            return if (remaining > 0L) remaining else null
        }
        return null
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
    private val elapsedRealtimeClock: () -> Long = {
        try {
            android.os.SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    },
    private val pollingIntervalMs: Long = 1000L
) {
    private val mutex = Mutex()
    private var refreshJob: Job? = null
    private var isScreenActive = false
    private val speedEngine = ChargingSpeedEngine()
    private val rollingHistoryBuffer = ArrayDeque<LiveChargingSample>(300)
    private var sessionStartElapsedRealtime: Long = 0L

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
                        currentBatteryPercent = hardwareProvider.getBatteryLevelPercent(),
                        currentTemperatureCelsius = hardwareProvider.getTemperatureCelsius(),
                        currentVoltageMv = null,
                        currentCurrentMa = null,
                        currentPowerWatts = null,
                        etaDisplayStatus = "Unavailable"
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
                        currentVoltageV = null,
                        currentVoltageMv = null,
                        currentCurrentMa = null,
                        pluggedSource = if (plugged > 0) "IDLE" else "UNPLUGGED",
                        currentBatteryPercent = hardwareProvider.getBatteryLevelPercent(),
                        currentTemperatureCelsius = hardwareProvider.getTemperatureCelsius(),
                        etaDisplayStatus = "Unavailable",
                        estimatedTimeToFullSeconds = null
                    )
                }
            }
        }
    }

    private fun startNewSessionLocked() {
        val now = clock()
        sessionStartElapsedRealtime = elapsedRealtimeClock()
        val currentLevel = hardwareProvider.getBatteryLevelPercent()
        val currentTemp = hardwareProvider.getTemperatureCelsius()
        val plugged = hardwareProvider.getPluggedType()

        _sessionState.value = LiveChargingSessionState(
            isChargingActive = true,
            sessionStartTimeMs = now,
            sessionElapsedRealtimeMs = sessionStartElapsedRealtime,
            sessionDurationSeconds = 0L,
            currentBatteryPercent = currentLevel,
            currentTemperatureCelsius = currentTemp,
            pluggedSource = plugged,
            lastUpdatedTimeMs = now,
            rollingHistory = rollingHistoryBuffer.toList(),
            isDischarging = false,
            etaDisplayStatus = "Calculating..."
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
                            isDischarging = !hardwareProvider.isCharging(),
                            etaDisplayStatus = "Unavailable"
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
        val temperatureCelsius = hardwareProvider.getTemperatureCelsius()
        val voltageMv = hardwareProvider.getVoltageMv()
        val rawCurrentMicroAmps = hardwareProvider.getCurrentMicroAmps()
        val isFull = hardwareProvider.isBatteryFull()

        val startTime = _sessionState.value.sessionStartTimeMs ?: now
        val durationSeconds = if (sessionStartElapsedRealtime > 0L) {
            (elapsedRealtimeClock() - sessionStartElapsedRealtime).coerceAtLeast(0L) / 1000L
        } else {
            (now - startTime).coerceAtLeast(0L) / 1000L
        }

        if (!isCharging) {
            _sessionState.value = _sessionState.value.copy(
                isChargingActive = false,
                isDischarging = true,
                currentBatteryPercent = levelPercent,
                currentTemperatureCelsius = temperatureCelsius,
                pluggedSource = plugged,
                currentPowerWatts = null,
                currentCurrentA = null,
                currentVoltageV = null,
                currentVoltageMv = null,
                currentCurrentMa = null,
                etaDisplayStatus = "Unavailable",
                estimatedTimeToFullSeconds = null
            )
            stopPollingLoopLocked()
            return
        }

        // Validate voltage within plausible hardware ranges (2.0V - 15.0V / 2000mV - 15000mV)
        val voltageMvFloat = voltageMv?.let {
            if (it in 2000..15000) it.toFloat() else null
        }
        val voltageV = voltageMvFloat?.let { it / 1000.0f }

        // Normalize current based on device fuel-gauge unit conventions
        val currentMa = rawCurrentMicroAmps?.let { raw ->
            if (raw == Int.MIN_VALUE) null
            else speedEngine.normalizeToMilliAmps(raw, activeCurrentFlow = true)
        }
        val currentMaFloat = currentMa?.let {
            val ma = abs(it).toFloat()
            if (ma in 0.0f..30000.0f) ma else null
        }
        val currentA = currentMaFloat?.let { it / 1000.0f }

        // Calculate power strictly from compatible valid voltage and current:
        // Power (W) = Voltage (mV) × Current (mA) / 1,000,000
        val powerWatts = if (voltageMvFloat != null && currentMaFloat != null) {
            (voltageMvFloat * currentMaFloat) / 1_000_000f
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
            isValid = voltageV != null || currentA != null || levelPercent != null,
            voltageMv = voltageMvFloat,
            currentMa = currentMaFloat,
            temperatureCelsius = temperatureCelsius
        )

        if (rollingHistoryBuffer.size >= 300) {
            rollingHistoryBuffer.removeFirst()
        }
        rollingHistoryBuffer.addLast(sample)

        // Estimated Time to Full computation per Section 9
        val (etaSeconds, etaStatus) = calculateTimeToFull(
            isFull = isFull,
            currentPercent = levelPercent,
            samples = rollingHistoryBuffer
        )

        _sessionState.value = _sessionState.value.copy(
            isChargingActive = true,
            sessionStartTimeMs = startTime,
            sessionElapsedRealtimeMs = sessionStartElapsedRealtime,
            sessionDurationSeconds = durationSeconds,
            currentVoltageV = voltageV,
            currentCurrentA = currentA,
            currentVoltageMv = voltageMvFloat,
            currentCurrentMa = currentMaFloat,
            currentPowerWatts = powerWatts,
            currentBatteryPercent = levelPercent,
            currentTemperatureCelsius = temperatureCelsius,
            pluggedSource = plugged,
            lastUpdatedTimeMs = now,
            rollingHistory = rollingHistoryBuffer.toList(),
            isDischarging = false,
            estimatedTimeToFullSeconds = etaSeconds,
            isFull = isFull,
            etaDisplayStatus = etaStatus
        )
    }

    /**
     * Calculates estimated time to full per Section 9 specification:
     * - If full -> "00:00:00"
     * - Uses rolling observations to compute charging rate (% per second)
     * - Formula: Estimated Time (seconds) = (100 - Current Percentage) / Charging Rate (% per second)
     * - Falls back to hardware estimate or "Calculating..." / "Unavailable"
     */
    private fun calculateTimeToFull(
        isFull: Boolean,
        currentPercent: Float?,
        samples: List<LiveChargingSample>
    ): Pair<Long?, String> {
        if (isFull || (currentPercent != null && currentPercent >= 100f)) {
            return 0L to "00:00:00"
        }

        if (currentPercent == null) {
            return null to "Unavailable"
        }

        // Look at rolling window of valid observations
        val validSamples = samples.filter { it.batteryPercent != null && (it.batteryPercent in 0f..100f) }
        val first = validSamples.firstOrNull()
        val last = validSamples.lastOrNull()

        if (first != null && last != null && validSamples.size >= 4) {
            val elapsedSeconds = (last.timestamp - first.timestamp) / 1000.0
            val deltaPercent = (last.batteryPercent ?: 0f) - (first.batteryPercent ?: 0f)

            // If we have at least 10 seconds of observation and battery has increased
            if (elapsedSeconds >= 10.0 && deltaPercent > 0.005f) {
                val ratePercentPerSec = deltaPercent / elapsedSeconds
                val remainingPercent = (100.0f - (last.batteryPercent ?: 0f)).coerceAtLeast(0f)
                val etaSeconds = (remainingPercent / ratePercentPerSec).toLong().coerceIn(0L, 86400L)
                val h = etaSeconds / 3600
                val m = (etaSeconds % 3600) / 60
                val s = etaSeconds % 60
                val formatted = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
                return etaSeconds to formatted
            }
        }

        // Fallback to system hardware estimate if available
        val sysEstimateMs = hardwareProvider.computeChargeTimeRemainingMillis()
        if (sysEstimateMs != null && sysEstimateMs > 0L) {
            val etaSeconds = (sysEstimateMs / 1000L).coerceIn(0L, 86400L)
            val h = etaSeconds / 3600
            val m = (etaSeconds % 3600) / 60
            val s = etaSeconds % 60
            val formatted = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
            return etaSeconds to formatted
        }

        return null to "Calculating..."
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
        sessionStartElapsedRealtime = 0L
    }
}
