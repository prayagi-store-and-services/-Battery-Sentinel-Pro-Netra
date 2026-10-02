package com.example

import com.example.util.BluetoothStatus
import com.example.util.bluetoothStatus
import com.example.util.bluetoothStatusMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothStatusTest {
    @Test fun noAdapterIsUnsupported() = assertEquals(BluetoothStatus.UNSUPPORTED, bluetoothStatus(false, true, true))
    @Test fun missingPermissionComesBeforeOff() = assertEquals(BluetoothStatus.PERMISSION_REQUIRED, bluetoothStatus(true, false, false))
    @Test fun offWhenPermittedButDisabled() = assertEquals(BluetoothStatus.OFF, bluetoothStatus(true, true, false))
    @Test fun onWhenEverythingIsAvailable() = assertEquals(BluetoothStatus.ON, bluetoothStatus(true, true, true))
    @Test fun offMessageTellsUserToTurnOn() = assertNotNull(bluetoothStatusMessage(BluetoothStatus.OFF)?.takeIf { it.contains("on karein") })
    @Test fun onHasNoMessage() = assertNull(bluetoothStatusMessage(BluetoothStatus.ON))
}
