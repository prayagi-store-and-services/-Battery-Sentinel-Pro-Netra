package com.example.model

enum class MobileNetworkGeneration {
    FIVE_G,
    FOUR_G_LTE,
    THREE_G,
    TWO_G,
    UNKNOWN;

    val label: String
        get() = when (this) {
            FIVE_G -> "5G"
            FOUR_G_LTE -> "4G/LTE"
            THREE_G -> "3G"
            TWO_G -> "2G"
            UNKNOWN -> "Unknown"
        }
}

enum class NetworkOptimizationStatus {
    IDLE,
    EVALUATING,
    HIGH_TRAFFIC_SKIPPED,       // >= 2 MB/s
    UNRELIABLE_TRAFFIC_SKIPPED, // Stale or unavailable
    SIM_NOT_READY_SKIPPED,      // SIM not ready or no service
    ACTIVE_TRANSFER_SKIPPED,    // Active transfer detected
    UNSUPPORTED_SKIPPED,        // Device / carrier does not support direct switching
    PERMISSION_REQUIRED_SKIPPED,// Requires privileged permission (MODIFY_PHONE_STATE/carrier privilege)
    PENDING_SWITCH,             // Debounced / evaluating
    OPTIMIZED_TO_4G,
    OPTIMIZED_TO_3G,
    RESTORED,
    USER_CHANGED_PRESERVED,
    FAILED
}

data class NetworkOptimizationState(
    val currentGeneration: MobileNetworkGeneration = MobileNetworkGeneration.UNKNOWN,
    val isEvaluating: Boolean = false,
    val isOptimizationActive: Boolean = false,
    val originalGeneration: MobileNetworkGeneration? = null,
    val targetGeneration: MobileNetworkGeneration? = null,
    val lastTrafficBytesPerSec: Long? = null,
    val status: NetworkOptimizationStatus = NetworkOptimizationStatus.IDLE,
    val limitationNotice: String? = "Standard Android public APIs require privileged carrier or system permissions (MODIFY_PHONE_STATE) to change the preferred network mode. Sentinel evaluates network traffic and conditions truthfully, reporting safe limitations.",
    val lastEvaluatedAt: Long = 0L
)
