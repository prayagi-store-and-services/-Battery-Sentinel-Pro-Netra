package com.example

import com.example.service.btConnectedLine
import com.example.service.btDisconnectedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BluetoothAnnouncementTextTest {
    @Test fun disconnectNeverSaysBatteryPercent() {
        assertEquals("Buds disconnected.", btDisconnectedLine("Buds"))
        assertFalse(btDisconnectedLine("Buds").contains("percent"))
    }
    @Test fun connectSaysPercentOnlyWhenKnown() {
        assertEquals("Buds connected, 60 percent.", btConnectedLine("Buds", 60))
        assertEquals("Buds connected.", btConnectedLine("Buds", null))
        assertEquals("Buds connected.", btConnectedLine("Buds", 0))
    }
    @Test fun blankNameFallsBack() {
        assertEquals("Bluetooth device disconnected.", btDisconnectedLine(" "))
        assertEquals("Bluetooth device connected.", btConnectedLine(null, null))
    }
}
