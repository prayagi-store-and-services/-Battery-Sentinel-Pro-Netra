package com.example.model

/** Positive sightings are not a complete device census. Android may return empty on error. */
enum class BluetoothObservationState { UNAVAILABLE, OBSERVED, PERMISSION_REQUIRED, OFF, UNSUPPORTED }
data class BluetoothObservation(
    val state: BluetoothObservationState,
    val devices: List<BluetoothDeviceItem> = emptyList(),
    val sequence: Long = 0L
)
