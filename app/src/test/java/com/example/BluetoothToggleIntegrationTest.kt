package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NetraCentralEvent
import com.example.model.NetraEventType
import com.example.service.AnnouncementEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BluetoothToggleIntegrationTest {
    @Before fun prepare() {
        val repo=NetraApplication.instance.settingsRepository
        repo.setAnnouncementsMasterEnabled(true);repo.setNightProtectionEnabled(false)
        repo.setAnnounceBluetoothBattery(false)
    }
    private fun checkDisabled(type:NetraEventType) {
        val context=ApplicationProvider.getApplicationContext<Context>();val engine=AnnouncementEngine(context)
        try {
            val method=AnnouncementEngine::class.java.getDeclaredMethod("handleCanonicalEvent",NetraCentralEvent::class.java).apply{isAccessible=true}
            method.invoke(engine,NetraCentralEvent("test-disabled-$type",type,System.currentTimeMillis(),"60","Test buds","Test"))
            val field=AnnouncementEngine::class.java.getDeclaredField("queue").apply{isAccessible=true}
            assertTrue("Bluetooth toggle OFF must suppress $type",(field.get(engine) as java.util.PriorityQueue<*>).isEmpty())
        } finally {engine.shutdown()}
    }
    @Test fun disabledBluetoothToggleSuppressesConnect()=checkDisabled(NetraEventType.BLUETOOTH_CONNECTED)
    @Test fun disabledBluetoothToggleSuppressesDisconnect()=checkDisabled(NetraEventType.BLUETOOTH_DISCONNECTED)

    private fun checkBatteryQueue(nameA:String,nameB:String) {
        val repo=NetraApplication.instance.settingsRepository;repo.setAnnounceBluetoothBattery(true)
        val engine=AnnouncementEngine(ApplicationProvider.getApplicationContext<Context>())
        try {
            val method=AnnouncementEngine::class.java.getDeclaredMethod("handleCanonicalEvent",NetraCentralEvent::class.java).apply{isAccessible=true}
            method.invoke(engine,NetraCentralEvent("a-80",NetraEventType.BLUETOOTH_BATTERY_BOUNDARY,previousValue=nameA,newValue="80",identityKey="TEST-A"))
            method.invoke(engine,NetraCentralEvent("b-80",NetraEventType.BLUETOOTH_BATTERY_BOUNDARY,previousValue=nameB,newValue="80",identityKey="TEST-B"))
            val field=AnnouncementEngine::class.java.getDeclaredField("queue").apply{isAccessible=true}
            assertEquals("A second device must not erase the first pending battery line",2,(field.get(engine) as java.util.PriorityQueue<*>).size)
            method.invoke(engine,NetraCentralEvent("a-70",NetraEventType.BLUETOOTH_BATTERY_BOUNDARY,previousValue=nameA,newValue="70",identityKey="TEST-A"))
            assertEquals("A newer reading replaces only that device's line",2,(field.get(engine) as java.util.PriorityQueue<*>).size)
        } finally {engine.shutdown()}
    }
    @Test fun differentDevicesKeepSeparatePendingBatteryLines()=checkBatteryQueue("Test buds A","Test buds B")
    @Test fun identicalDeviceNamesStillKeepSeparatePendingBatteryLines()=checkBatteryQueue("Test buds","Test buds")
}
