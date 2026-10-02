package com.example.util

/** What the Devices screen should say about Bluetooth, decided from three facts only. */
enum class BluetoothStatus { UNSUPPORTED, PERMISSION_REQUIRED, OFF, ON }

fun bluetoothStatus(hasAdapter: Boolean, permissionGranted: Boolean, enabled: Boolean): BluetoothStatus = when {
    !hasAdapter -> BluetoothStatus.UNSUPPORTED
    !permissionGranted -> BluetoothStatus.PERMISSION_REQUIRED
    !enabled -> BluetoothStatus.OFF
    else -> BluetoothStatus.ON
}

fun bluetoothStatusMessage(status: BluetoothStatus): String? = when (status) {
    BluetoothStatus.UNSUPPORTED -> "Is phone mein Bluetooth hardware nahin mila (Unavailable)."
    BluetoothStatus.PERMISSION_REQUIRED -> "Bluetooth permission required hai - kripya on karein, tabhi connected devices aur battery dikhegi."
    BluetoothStatus.OFF -> "Bluetooth band hai - on karein, tabhi connected devices dikhenge."
    BluetoothStatus.ON -> null
}
