package com.example

import com.example.model.BluetoothDeviceItem
import com.example.model.NetraCentralEvent
import com.example.model.NetraEventType
import com.example.service.NetraCentralDataCenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BluetoothCanonicalEventsTest {
    private fun device(pct:Int?, address:String="TEST-A", name:String="Test buds") = BluetoothDeviceItem(
        name=name,address=address,isConnected=true,isPaired=true,deviceType="Test",batteryPercent=pct,profile="Test")

    @Test fun skippedTenPercentBoundaryAnnouncesActualObservedLevel() = runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.centralEvents.toList(events) }
        center.processBluetoothDevices(listOf(device(89)));events.clear()
        center.processBluetoothDevices(listOf(device(79)))
        val event=events.single { it.eventType==NetraEventType.BLUETOOTH_BATTERY_BOUNDARY }
        assertEquals("79",event.newValue)
    }
    @Test fun reconnectDoesNotInventOldBatteryOrExtraBoundaryLine() = runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.centralEvents.toList(events) }
        center.processBluetoothDevices(listOf(device(80)));center.processBluetoothDevices(emptyList());events.clear()
        center.processBluetoothDevices(listOf(device(null)))
        val event=events.single { it.eventType==NetraEventType.BLUETOOTH_CONNECTED }
        assertNull(event.previousValue)
        assertTrue(events.none {it.eventType==NetraEventType.BLUETOOTH_BATTERY_BOUNDARY})
    }
    @Test fun duplicateAddressCannotProduceTwoConnectionEvents() = runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.centralEvents.toList(events) }
        center.processBluetoothDevices(listOf(device(80),device(80)))
        assertEquals(1,events.count {it.eventType==NetraEventType.BLUETOOTH_CONNECTED})
        assertEquals(1,center.centralState.value.bluetoothDevices.size)
    }
    @Test fun distinctAddressesWithSameNameHaveIndependentBoundaryEvents() = runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.centralEvents.toList(events) }
        center.processBluetoothDevices(listOf(device(89),device(69,"TEST-B")));events.clear()
        center.processBluetoothDevices(listOf(device(79),device(59,"TEST-B")))
        assertEquals(2,events.count {it.eventType==NetraEventType.BLUETOOTH_BATTERY_BOUNDARY})
    }
    @Test fun unchangedUnknownAndSameBucketReadingsDoNotRepeatBoundary() = runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.centralEvents.toList(events) }
        center.processBluetoothDevices(listOf(device(89)));events.clear()
        for (pct in listOf(88,null,88,81)) center.processBluetoothDevices(listOf(device(pct)))
        assertTrue(events.none {it.eventType==NetraEventType.BLUETOOTH_BATTERY_BOUNDARY})
    }

    @Test fun skippedUpwardBoundaryReportsActualNotRoundedValue() = runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.centralEvents.toList(events) }
        center.processBluetoothDevices(listOf(device(59)));events.clear()
        center.processBluetoothDevices(listOf(device(61)))
        val event=events.single {it.eventType==NetraEventType.BLUETOOTH_BATTERY_BOUNDARY}
        assertEquals("61",event.newValue);assertEquals("TEST-A",event.identityKey)
    }
    @Test fun severalMissedBoundariesProduceOneActualReadingNotInventedHistory() = runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.centralEvents.toList(events) }
        center.processBluetoothDevices(listOf(device(85)));events.clear()
        center.processBluetoothDevices(listOf(device(25)));center.processBluetoothDevices(listOf(device(25)))
        val boundaries=events.filter {it.eventType==NetraEventType.BLUETOOTH_BATTERY_BOUNDARY}
        assertEquals(1,boundaries.size);assertEquals("25",boundaries.single().newValue)
    }
    @Test fun exactBoundaryAtConnectIsNotAnnouncedTwice() = runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { center.centralEvents.toList(events) }
        center.processBluetoothDevices(listOf(device(80)))
        assertEquals(1,events.size);assertEquals(NetraEventType.BLUETOOTH_CONNECTED,events.single().eventType)
    }
}
