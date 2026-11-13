package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.example.model.BluetoothObservationState

internal fun bluetoothObservationMessage(state: BluetoothObservationState): String = when (state) {
    BluetoothObservationState.OBSERVED -> "Only devices Android reports now are shown. A missing entry does not prove it disconnected."
    BluetoothObservationState.PERMISSION_REQUIRED -> "Bluetooth observations unavailable: allow Bluetooth access. No disconnect is inferred from missing permission."
    BluetoothObservationState.OFF -> "Bluetooth is off. No current device readings."
    BluetoothObservationState.UNSUPPORTED -> "Bluetooth hardware is unavailable on this phone."
    BluetoothObservationState.UNAVAILABLE -> "Bluetooth observations unavailable. Android may return no devices while a query fails or profiles reconnect; this does not prove a disconnect."
}

@Composable
internal fun BluetoothObservationNote(state: BluetoothObservationState) {
    Text(bluetoothObservationMessage(state), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
}
