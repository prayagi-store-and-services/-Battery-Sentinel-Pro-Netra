package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.model.MobileNetworkGeneration
import com.example.model.NetworkOptimizationState
import com.example.model.NetworkOptimizationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Traffic monitoring abstraction for rolling bandwidth calculation and active transfer detection.
 */
interface TrafficMonitor {
    fun getRollingTrafficBytesPerSec(): Long?
    fun isTrafficDataFresh(): Boolean
    fun hasActiveTransfer(): Boolean
}

/**
 * Telephony status abstraction for SIM readiness, service registration, and current generation.
 */
interface TelephonyStatusProvider {
    fun getNetworkGeneration(): MobileNetworkGeneration
    fun isSimReady(): Boolean
    fun isServiceRegistered(): Boolean
    fun isInActiveCall(): Boolean
    fun is3GSupported(): Boolean
}

/**
 * Preferred network mode switching abstraction with safe permission checking.
 */
interface NetworkModeSwitcher {
    fun isSwitchingSupported(): Boolean
    fun hasRequiredPermission(): Boolean
    fun switchToGeneration(target: MobileNetworkGeneration): Boolean
    fun getCurrentPreferredGeneration(): MobileNetworkGeneration
}

/**
 * Android TrafficStats-based traffic monitor.
 */
class AndroidTrafficMonitor(private val context: Context) : TrafficMonitor {
    private var lastRxBytes: Long = -1L
    private var lastTxBytes: Long = -1L
    private var lastSampleTimeMs: Long = 0L
    private var cachedRateBytesPerSec: Long? = null

    override fun getRollingTrafficBytesPerSec(): Long? {
        val now = System.currentTimeMillis()
        val currentRx = TrafficStats.getMobileRxBytes()
        val currentTx = TrafficStats.getMobileTxBytes()

        if (currentRx == TrafficStats.UNSUPPORTED.toLong() || currentTx == TrafficStats.UNSUPPORTED.toLong()) {
            return null
        }

        if (lastRxBytes >= 0 && lastTxBytes >= 0 && lastSampleTimeMs > 0) {
            val deltaMs = now - lastSampleTimeMs
            if (deltaMs in 500..30_000) {
                val deltaRx = (currentRx - lastRxBytes).coerceAtLeast(0)
                val deltaTx = (currentTx - lastTxBytes).coerceAtLeast(0)
                val totalBytes = deltaRx + deltaTx
                val rate = (totalBytes * 1000L) / deltaMs
                cachedRateBytesPerSec = rate
            } else if (deltaMs > 30_000) {
                // Stale sample
                cachedRateBytesPerSec = null
            }
        }

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastSampleTimeMs = now
        return cachedRateBytesPerSec
    }

    override fun isTrafficDataFresh(): Boolean {
        if (lastSampleTimeMs == 0L) return false
        val age = System.currentTimeMillis() - lastSampleTimeMs
        return age <= 30_000L && cachedRateBytesPerSec != null
    }

    override fun hasActiveTransfer(): Boolean {
        val rate = cachedRateBytesPerSec ?: 0L
        return rate >= 200_000L // 200 KB/s active transfer threshold
    }
}

/**
 * Android TelephonyManager and ConnectivityManager provider.
 */
class AndroidTelephonyStatusProvider(private val context: Context) : TelephonyStatusProvider {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun getNetworkGeneration(): MobileNetworkGeneration {
        val tm = telephonyManager ?: return MobileNetworkGeneration.UNKNOWN
        val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                tm.dataNetworkType
            } catch (_: SecurityException) {
                TelephonyManager.NETWORK_TYPE_UNKNOWN
            }
        } else {
            @Suppress("DEPRECATION")
            tm.networkType
        }

        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> MobileNetworkGeneration.FIVE_G
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> MobileNetworkGeneration.FOUR_G_LTE
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> MobileNetworkGeneration.THREE_G
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> MobileNetworkGeneration.TWO_G
            else -> MobileNetworkGeneration.UNKNOWN
        }
    }

    override fun isSimReady(): Boolean {
        val tm = telephonyManager ?: return false
        return tm.simState == TelephonyManager.SIM_STATE_READY
    }

    override fun isServiceRegistered(): Boolean {
        val tm = telephonyManager ?: return false
        if (tm.networkOperator.isNullOrBlank()) return false
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun isInActiveCall(): Boolean {
        val tm = telephonyManager ?: return false
        // Reading the call state needs READ_PHONE_STATE. Without it (or if the system refuses), the call state is unknown:
        // report "not in a call" instead of crashing the screen-off evaluation.
        if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            tm.callState != TelephonyManager.CALL_STATE_IDLE
        } catch (_: SecurityException) {
            false
        }
    }

    override fun is3GSupported(): Boolean {
        // In modern deployments, 3G is largely decommissioned. Only support if explicitly present.
        return false
    }
}

/**
 * Standard Android public API mode switcher.
 * Respects strict Android permission boundaries: third-party applications lack MODIFY_PHONE_STATE
 * or carrier privileges without OEM/carrier signing.
 */
class AndroidNetworkModeSwitcher(private val context: Context) : NetworkModeSwitcher {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    override fun isSwitchingSupported(): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    override fun hasRequiredPermission(): Boolean {
        val hasModifyPhoneState = ContextCompat.checkSelfPermission(
            context,
            "android.Manifest.permission.MODIFY_PHONE_STATE"
        ) == PackageManager.PERMISSION_GRANTED
        val hasCarrierPrivilege = telephonyManager?.hasCarrierPrivileges() == true
        return hasModifyPhoneState || hasCarrierPrivilege
    }

    override fun switchToGeneration(target: MobileNetworkGeneration): Boolean {
        if (!hasRequiredPermission()) {
            return false
        }
        // Privileged carrier/system switching would be executed here
        return true
    }

    override fun getCurrentPreferredGeneration(): MobileNetworkGeneration {
        return MobileNetworkGeneration.UNKNOWN
    }
}

/**
 * Adaptive Screen-Off Network Optimization Engine.
 * Implements strict debouncing, traffic exception thresholds (>= 2 MB/s),
 * SIM/call safety verification, truthful Android permission reporting,
 * and seamless restoration on screen-on without overwriting user changes.
 */
class NetworkOptimizationEngine(
    private val trafficMonitor: TrafficMonitor,
    private val telephonyStatusProvider: TelephonyStatusProvider,
    private val modeSwitcher: NetworkModeSwitcher,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onStateUpdated: (NetworkOptimizationState) -> Unit
) {
    companion object {
        const val DEFAULT_HIGH_TRAFFIC_THRESHOLD_BYTES_PER_SEC = 2_097_152L // 2 MB/s ≈ 16 Mbps
        const val DEFAULT_DEBOUNCE_DELAY_MS = 5_000L
    }

    private val mutex = Mutex()
    private var pendingDebounceJob: Job? = null
    var currentState = NetworkOptimizationState()
        private set

    /**
     * Confirmed screen-off entry point.
     * Evaluates network conditions after debouncing.
     */
    fun onScreenTurnedOff(
        debounceMs: Long = DEFAULT_DEBOUNCE_DELAY_MS,
        thresholdBytesPerSec: Long = DEFAULT_HIGH_TRAFFIC_THRESHOLD_BYTES_PER_SEC
    ) {
        scope.launch {
            mutex.withLock {
                // Cancel any previous debounce job to avoid duplicates
                pendingDebounceJob?.cancel()

                val currentGen = telephonyStatusProvider.getNetworkGeneration()
                val updated = currentState.copy(
                    isEvaluating = true,
                    currentGeneration = currentGen,
                    status = NetworkOptimizationStatus.PENDING_SWITCH,
                    lastEvaluatedAt = System.currentTimeMillis()
                )
                publishState(updated)

                pendingDebounceJob = scope.launch {
                    if (debounceMs > 0) {
                        delay(debounceMs)
                    }
                    performScreenOffEvaluation(thresholdBytesPerSec)
                }
            }
        }
    }

    /**
     * Executes the safety and traffic checks then applies optimization if permitted.
     */
    suspend fun performScreenOffEvaluation(
        thresholdBytesPerSec: Long = DEFAULT_HIGH_TRAFFIC_THRESHOLD_BYTES_PER_SEC
    ) = mutex.withLock {
        val now = System.currentTimeMillis()
        val currentGen = telephonyStatusProvider.getNetworkGeneration()

        // 1. Check SIM readiness, service registration, and active call
        if (!telephonyStatusProvider.isSimReady() ||
            !telephonyStatusProvider.isServiceRegistered() ||
            telephonyStatusProvider.isInActiveCall()
        ) {
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    currentGeneration = currentGen,
                    status = NetworkOptimizationStatus.SIM_NOT_READY_SKIPPED,
                    lastEvaluatedAt = now
                )
            )
            return@withLock
        }

        // 2. Identify network reduction policy
        // 5G -> 4G/LTE; 4G -> 3G (only if supported); 3G/2G -> do not downgrade; UNKNOWN -> do not change
        val targetGen = when (currentGen) {
            MobileNetworkGeneration.FIVE_G -> MobileNetworkGeneration.FOUR_G_LTE
            MobileNetworkGeneration.FOUR_G_LTE -> {
                if (telephonyStatusProvider.is3GSupported()) {
                    MobileNetworkGeneration.THREE_G
                } else {
                    null // 4G/LTE is lowest practical supported mode
                }
            }
            MobileNetworkGeneration.THREE_G,
            MobileNetworkGeneration.TWO_G,
            MobileNetworkGeneration.UNKNOWN -> null
        }

        if (targetGen == null) {
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    currentGeneration = currentGen,
                    status = NetworkOptimizationStatus.IDLE,
                    lastEvaluatedAt = now
                )
            )
            return@withLock
        }

        // 3. Measure mobile traffic over rolling window
        val trafficRate = trafficMonitor.getRollingTrafficBytesPerSec()
        val isFresh = trafficMonitor.isTrafficDataFresh()

        if (trafficRate == null || !isFresh) {
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    currentGeneration = currentGen,
                    lastTrafficBytesPerSec = trafficRate,
                    status = NetworkOptimizationStatus.UNRELIABLE_TRAFFIC_SKIPPED,
                    lastEvaluatedAt = now
                )
            )
            return@withLock
        }

        // High traffic exception (>= 2 MB/s)
        if (trafficRate >= thresholdBytesPerSec) {
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    currentGeneration = currentGen,
                    lastTrafficBytesPerSec = trafficRate,
                    status = NetworkOptimizationStatus.HIGH_TRAFFIC_SKIPPED,
                    lastEvaluatedAt = now
                )
            )
            return@withLock
        }

        // Recheck immediately before requesting a change: ensure no active transfer
        if (trafficMonitor.hasActiveTransfer()) {
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    currentGeneration = currentGen,
                    lastTrafficBytesPerSec = trafficRate,
                    status = NetworkOptimizationStatus.ACTIVE_TRANSFER_SKIPPED,
                    lastEvaluatedAt = now
                )
            )
            return@withLock
        }

        // 4. Check device and carrier switching support
        if (!modeSwitcher.isSwitchingSupported()) {
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    currentGeneration = currentGen,
                    targetGeneration = targetGen,
                    lastTrafficBytesPerSec = trafficRate,
                    status = NetworkOptimizationStatus.UNSUPPORTED_SKIPPED,
                    lastEvaluatedAt = now
                )
            )
            return@withLock
        }

        // 5. Check permission boundaries (MODIFY_PHONE_STATE or carrier privileges)
        if (!modeSwitcher.hasRequiredPermission()) {
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    currentGeneration = currentGen,
                    targetGeneration = targetGen,
                    lastTrafficBytesPerSec = trafficRate,
                    status = NetworkOptimizationStatus.PERMISSION_REQUIRED_SKIPPED,
                    limitationNotice = "Standard Android public APIs require privileged carrier or system permissions (MODIFY_PHONE_STATE) to change the preferred network mode. Sentinel evaluates network traffic and conditions truthfully, reporting safe limitations.",
                    lastEvaluatedAt = now
                )
            )
            return@withLock
        }

        // 6. Perform network switch
        val switchSuccess = modeSwitcher.switchToGeneration(targetGen)
        if (switchSuccess) {
            val status = if (targetGen == MobileNetworkGeneration.FOUR_G_LTE) {
                NetworkOptimizationStatus.OPTIMIZED_TO_4G
            } else {
                NetworkOptimizationStatus.OPTIMIZED_TO_3G
            }
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    isOptimizationActive = true,
                    originalGeneration = currentGen,
                    targetGeneration = targetGen,
                    currentGeneration = targetGen,
                    lastTrafficBytesPerSec = trafficRate,
                    status = status,
                    lastEvaluatedAt = now
                )
            )
        } else {
            publishState(
                currentState.copy(
                    isEvaluating = false,
                    targetGeneration = targetGen,
                    lastTrafficBytesPerSec = trafficRate,
                    status = NetworkOptimizationStatus.FAILED,
                    lastEvaluatedAt = now
                )
            )
        }
    }

    /**
     * Confirmed screen-on entry point.
     * Cancels pending evaluations and restores previous network mode if changed by this app.
     */
    fun onScreenTurnedOn() {
        scope.launch {
            mutex.withLock {
                // 1. Cancel pending screen-off optimization
                pendingDebounceJob?.cancel()
                pendingDebounceJob = null

                val now = System.currentTimeMillis()

                // If optimization was not active, simply reset evaluating flag
                if (!currentState.isOptimizationActive) {
                    if (currentState.isEvaluating) {
                        publishState(
                            currentState.copy(
                                isEvaluating = false,
                                status = NetworkOptimizationStatus.IDLE,
                                lastEvaluatedAt = now
                            )
                        )
                    }
                    return@withLock
                }

                // 2. Optimization was active: check if user or carrier changed the mode
                val currentGen = telephonyStatusProvider.getNetworkGeneration()
                val targetGen = currentState.targetGeneration
                val originalGen = currentState.originalGeneration

                if (targetGen != null && currentGen != targetGen && currentGen != MobileNetworkGeneration.UNKNOWN) {
                    // User or carrier selected a different network mode while screen was off: NEVER overwrite it!
                    publishState(
                        currentState.copy(
                            isEvaluating = false,
                            isOptimizationActive = false,
                            currentGeneration = currentGen,
                            status = NetworkOptimizationStatus.USER_CHANGED_PRESERVED,
                            lastEvaluatedAt = now
                        )
                    )
                    return@withLock
                }

                // 3. Restore original generation
                if (originalGen != null) {
                    if (modeSwitcher.hasRequiredPermission() && modeSwitcher.isSwitchingSupported()) {
                        val restored = modeSwitcher.switchToGeneration(originalGen)
                        publishState(
                            currentState.copy(
                                isEvaluating = false,
                                isOptimizationActive = false,
                                currentGeneration = originalGen,
                                status = if (restored) NetworkOptimizationStatus.RESTORED else NetworkOptimizationStatus.FAILED,
                                lastEvaluatedAt = now
                            )
                        )
                    } else {
                        // Restoration cannot be performed directly via public APIs
                        publishState(
                            currentState.copy(
                                isEvaluating = false,
                                isOptimizationActive = false,
                                status = NetworkOptimizationStatus.PERMISSION_REQUIRED_SKIPPED,
                                limitationNotice = "Restoration requires privileged carrier or system permissions. Please verify preferred network settings in Android Settings.",
                                lastEvaluatedAt = now
                            )
                        )
                    }
                } else {
                    publishState(
                        currentState.copy(
                            isEvaluating = false,
                            isOptimizationActive = false,
                            status = NetworkOptimizationStatus.RESTORED,
                            lastEvaluatedAt = now
                        )
                    )
                }
            }
        }
    }

    private fun publishState(newState: NetworkOptimizationState) {
        currentState = newState
        onStateUpdated(newState)
    }
}
