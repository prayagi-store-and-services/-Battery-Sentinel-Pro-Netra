package com.example
import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.*
import com.example.service.NetraCentralDataCenter
import com.example.util.BluetoothHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BluetoothObservationTest {
    @Test fun deniedPermissionCannotBecomeAnObservedDisconnect() = runTest {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val center=NetraCentralDataCenter()
        val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){center.centralEvents.toList(events)}
        center.processBluetoothDevices(listOf(BluetoothDeviceItem("Test buds","TEST-A",true,true,"Test",60,"Test")))
        events.clear()
        shadowOf(c as android.app.Application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        center.processBluetoothObservation(BluetoothHelper.observe(c))
        assertTrue("Permission loss is unknown, not a real disconnect",events.none{it.eventType==NetraEventType.BLUETOOTH_DISCONNECTED})
    }

    private fun device(address:String="TEST-A")=BluetoothDeviceItem("Test buds",address,true,true,"Test",60,"Test")
    @Test fun emptyObservationDoesNotProveDisconnectAndClearsLiveView()=runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){center.centralEvents.toList(events)}
        center.processBluetoothDevices(listOf(device()));events.clear()
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.UNAVAILABLE))
        assertTrue(events.isEmpty());assertNull(center.centralState.value.bluetoothConnected)
        assertTrue(center.centralState.value.bluetoothDevices.isEmpty())
        assertEquals(BluetoothObservationState.UNAVAILABLE,center.centralState.value.bluetoothObservationState)
    }
    @Test fun recoverySightingsDoNotInventAReconnect()=runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){center.centralEvents.toList(events)}
        center.processBluetoothDevices(listOf(device()));events.clear()
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.PERMISSION_REQUIRED))
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.OBSERVED,listOf(device())))
        assertTrue(events.isEmpty());assertEquals(true,center.centralState.value.bluetoothConnected)
    }
    @Test fun oneMissingSightingCannotDisconnectAnotherDevice()=runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){center.centralEvents.toList(events)}
        center.processBluetoothDevices(listOf(device(),device("TEST-B")));events.clear()
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.OBSERVED,listOf(device())))
        assertTrue(events.none{it.eventType==NetraEventType.BLUETOOTH_DISCONNECTED})
    }
    @Test fun explicitAclDisconnectAffectsOnlyItsIdentity()=runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){center.centralEvents.toList(events)}
        center.processBluetoothDevices(listOf(device(),device("TEST-B")));events.clear()
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.UNAVAILABLE),disconnectedIdentity="TEST-A")
        assertEquals(1,events.size);assertEquals("TEST-A",events.single().identityKey)
        assertEquals(NetraEventType.BLUETOOTH_DISCONNECTED,events.single().eventType)
    }
    @Test fun confirmedAdapterOffCanDisconnectPreviouslyObservedDevicesOnce()=runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){center.centralEvents.toList(events)}
        center.processBluetoothDevices(listOf(device()));events.clear()
        repeat(2){center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.OFF))}
        assertEquals(1,events.size);assertEquals(NetraEventType.BLUETOOTH_DISCONNECTED,events.single().eventType)
        assertEquals(false,center.centralState.value.bluetoothConnected)
    }
    @Test fun connectEvidenceCannotAnnounceOtherNewlySeenDevices()=runTest {
        val center=NetraCentralDataCenter();val events=mutableListOf<NetraCentralEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){center.centralEvents.toList(events)}
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.OBSERVED,listOf(device(),device("TEST-B"))),connectedIdentity="TEST-A")
        assertEquals(1,events.size);assertEquals("TEST-A",events.single().identityKey)
    }

    @Test fun delayedOlderObservationCannotOverwriteNewerSightings()=runTest {
        val center=NetraCentralDataCenter()
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.OBSERVED,listOf(device()),sequence=2))
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.UNAVAILABLE,sequence=1))
        assertEquals(1,center.centralState.value.bluetoothDevices.size)
        assertEquals(BluetoothObservationState.OBSERVED,center.centralState.value.bluetoothObservationState)
    }

    private fun helperAtAdapterState(state:Int):BluetoothObservation {
        val app=ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val adapter=android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        assertNotNull(adapter);shadowOf(adapter).setState(state)
        return BluetoothHelper.observe(app)
    }
    @Test fun adapterTurningOffIsUnknownNotConfirmedOff() {
        assertEquals(BluetoothObservationState.UNAVAILABLE,helperAtAdapterState(android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF).state)
    }
    @Test fun confirmedAdapterOffHasAnExplicitState() {
        assertEquals(BluetoothObservationState.OFF,helperAtAdapterState(android.bluetooth.BluetoothAdapter.STATE_OFF).state)
    }
    @Test fun enabledButEmptyProfilesAreUnknownNotConfirmedEmpty() {
        assertEquals(BluetoothObservationState.UNAVAILABLE,helperAtAdapterState(android.bluetooth.BluetoothAdapter.STATE_ON).state)
    }
    @Test fun observationSequenceIsMonotonicAcrossHelperCalls() {
        val first=helperAtAdapterState(android.bluetooth.BluetoothAdapter.STATE_OFF)
        val second=helperAtAdapterState(android.bluetooth.BluetoothAdapter.STATE_OFF)
        assertTrue(second.sequence>first.sequence)
    }
    @Test fun staleBatteryPublicationCannotEraseObservationAvailability()=runTest {
        val center=NetraCentralDataCenter();val stale=center.centralState.value
        center.processBluetoothObservation(BluetoothObservation(BluetoothObservationState.PERMISSION_REQUIRED))
        center.publishTelemetryState(stale)
        assertEquals(BluetoothObservationState.PERMISSION_REQUIRED,center.centralState.value.bluetoothObservationState)
    }
}
