package com.example
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NetraCentralEvent
import com.example.model.NetraEventType
import com.example.service.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BluetoothSilenceRegressionTest {
 @Before fun prepare(){NetraApplication.instance.settingsRepository.apply{
  setAnnouncementsMasterEnabled(true);setNightProtectionEnabled(false);setAnnounceBluetoothBattery(true)
 }}
 private fun assertSilent(type:NetraEventType){
  val engine=AnnouncementEngine(ApplicationProvider.getApplicationContext<Context>())
  try{
   AnnouncementEngine::class.java.getDeclaredMethod("handleCanonicalEvent",NetraCentralEvent::class.java).apply{isAccessible=true}.invoke(engine,
    NetraCentralEvent("silence-$type",type,previousValue=if(type==NetraEventType.BLUETOOTH_BATTERY_BOUNDARY)"Test buds" else "60",newValue=if(type==NetraEventType.BLUETOOTH_BATTERY_BOUNDARY)"60" else "Test buds"))
   assertTrue("No BT event may enter speech queue",queue(engine).isEmpty())
  }finally{engine.shutdown()}
 }
 private fun queue(e:AnnouncementEngine)=AnnouncementEngine::class.java.getDeclaredField("queue").apply{isAccessible=true}.get(e) as java.util.PriorityQueue<*>
 @Test fun connectedNeverSpeaksEvenWithOldPreferenceTrue()=assertSilent(NetraEventType.BLUETOOTH_CONNECTED)
 @Test fun disconnectedNeverSpeaksEvenWithOldPreferenceTrue()=assertSilent(NetraEventType.BLUETOOTH_DISCONNECTED)
 @Test fun batteryNeverSpeaksEvenWithOldPreferenceTrue()=assertSilent(NetraEventType.BLUETOOTH_BATTERY_BOUNDARY)
 @Test fun staleOrDirectBluetoothQueueInputIsRejected(){
  val engine=AnnouncementEngine(ApplicationProvider.getApplicationContext<Context>())
  try{engine.enqueue(AnnouncementItem("stale-bt","Test buds connected",AnnouncementPriority.BLUETOOTH_STATE,"BLUETOOTH_STATE",isNightException=true))
   engine.enqueue(AnnouncementItem("stale-battery","Test buds60percent",AnnouncementPriority.BLUETOOTH_BATTERY,"BLUETOOTH_BATTERY"))
   assertTrue(queue(engine).isEmpty())
  }finally{engine.shutdown()}
 }
 @Test fun oldPersistedPreferenceCannotReenableAfterRepositoryReload(){
  val c=ApplicationProvider.getApplicationContext<Context>()
  c.getSharedPreferences("netra_sentinel_prefs",Context.MODE_PRIVATE).edit().putBoolean("announce_bt_battery",true).commit()
  val repo=com.example.data.repository.SettingsRepository(c)
  assertFalse(repo.settings.value.announceBluetoothBattery)
  repo.setAnnounceBluetoothBattery(true)
  assertFalse(repo.settings.value.announceBluetoothBattery)
  assertFalse(com.example.data.repository.SettingsRepository(c).settings.value.announceBluetoothBattery)
 }
 @Test fun bluetoothCategoryBlockedEvenWithUnrelatedPriority(){
  val engine=AnnouncementEngine(ApplicationProvider.getApplicationContext<Context>())
  try{for(category in listOf("BLUETOOTH_STATE","BLUETOOTH_BATTERY")){
   engine.enqueue(AnnouncementItem("category-$category","Device state",AnnouncementPriority.INFORMATIONAL,category))
  };assertTrue(queue(engine).isEmpty())}finally{engine.shutdown()}
 }
 @Test fun bluetoothPriorityBlockedEvenWithUnrelatedCategory(){
  val engine=AnnouncementEngine(ApplicationProvider.getApplicationContext<Context>())
  try{for(priority in listOf(AnnouncementPriority.BLUETOOTH_STATE,AnnouncementPriority.BLUETOOTH_BATTERY)){
   engine.enqueue(AnnouncementItem("priority-$priority","Device state",priority,"COMPATIBILITY"))
  };assertTrue(queue(engine).isEmpty())}finally{engine.shutdown()}
 }
 @Test fun ordinaryCriticalThermalStillQueues(){
  val engine=AnnouncementEngine(ApplicationProvider.getApplicationContext<Context>())
  try{engine.enqueue(AnnouncementItem("thermal-kept","Critical temperature45degrees",AnnouncementPriority.CRITICAL_THERMAL,"THERMAL_CRITICAL",isNightException=true));assertFalse(queue(engine).isEmpty())}finally{engine.shutdown()}
 }
 @Test fun normalPhoneAnnouncementStillQueues(){
  val engine=AnnouncementEngine(ApplicationProvider.getApplicationContext<Context>())
  try{engine.enqueue(AnnouncementItem("phone-kept","Battery60percent",AnnouncementPriority.PHONE_BATTERY,"PHONE_BATTERY"));assertFalse(queue(engine).isEmpty())}finally{engine.shutdown()}
 }
}
