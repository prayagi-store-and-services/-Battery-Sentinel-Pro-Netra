package com.example

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import com.example.service.BatteryMonitorService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothReceiverBoundaryTest {
    @Test fun bluetoothEventsUseExportedReceiverWhileBatteryInputStaysPrivate() {
        val controller = Robolectric.buildService(BatteryMonitorService::class.java)
        val service = controller.get()
        try {
            val start = BatteryMonitorService::class.java.getDeclaredMethod("startCollectorsAndPolling").apply { isAccessible = true }
            start.invoke(service)
            val registrations = shadowOf(service.application).registeredReceivers.filter { it.broadcastReceiver.javaClass.name.startsWith("com.example.service.BatteryMonitorService$") }
            val bt = registrations.single { it.intentFilter.hasAction(BluetoothDevice.ACTION_ACL_CONNECTED) }
            val battery = registrations.single { it.intentFilter.hasAction(Intent.ACTION_BATTERY_CHANGED) }
            assertNotSame("Privileged Bluetooth sender needs a separate receiver boundary", battery.broadcastReceiver, bt.broadcastReceiver)
            assertEquals(Context.RECEIVER_EXPORTED, bt.flags and Context.RECEIVER_EXPORTED)
            assertEquals(Context.RECEIVER_NOT_EXPORTED, battery.flags and Context.RECEIVER_NOT_EXPORTED)
            assertEquals("Only four protected platform Bluetooth actions cross this boundary", 4, bt.intentFilter.countActions())
            assertFalse(bt.intentFilter.hasAction(Intent.ACTION_POWER_CONNECTED))
            assertFalse(battery.intentFilter.hasAction(BluetoothDevice.ACTION_ACL_CONNECTED))
            start.invoke(service)
            assertEquals("Supervisor restart must not duplicate Bluetooth registration", 1,
                shadowOf(service.application).registeredReceivers.count { it.broadcastReceiver.javaClass.name.startsWith("com.example.service.BatteryMonitorService$") && it.intentFilter.hasAction(BluetoothDevice.ACTION_ACL_CONNECTED) })
        } finally {
            service.onDestroy()
        }
        assertFalse("Both receiver boundaries must be removed on stop", shadowOf(service.application).registeredReceivers.any { it.broadcastReceiver.javaClass.name.startsWith("com.example.service.BatteryMonitorService$") })
    }
}
