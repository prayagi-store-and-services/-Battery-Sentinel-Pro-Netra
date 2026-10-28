package com.example
import android.app.Application
import com.example.update.AutomaticUpdateNotice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class AutomaticUpdateNoticeTest {
 @Test fun cadenceHandlesFirstRunBoundaryAndClockRollback(){
  val day=AutomaticUpdateNotice.INTERVAL_MS
  assertTrue(AutomaticUpdateNotice.due(100,0))
  assertFalse(AutomaticUpdateNotice.due(100+day-1,100))
  assertTrue(AutomaticUpdateNotice.due(100+day,100))
  assertTrue(AutomaticUpdateNotice.due(99,100))
 }
 @Test fun dismissedVersionDoesNotSuppressNextVersion(){
  val c=RuntimeEnvironment.getApplication()
  AutomaticUpdateNotice.dismiss(c,99)
  assertTrue(AutomaticUpdateNotice.wasDismissed(c,99))
  assertFalse(AutomaticUpdateNotice.wasDismissed(c,100))
 }
 @Test fun cachedCheckDoesNotNeedNotificationPermissionOrNetwork(){
  val c=RuntimeEnvironment.getApplication()
  c.getSharedPreferences("eco_metadata_notice_v1",0).edit().clear().putLong("attempt",System.currentTimeMillis()).putLong("code",9999).putString("name","9.9").commit()
  assertEquals(9999L, AutomaticUpdateNotice.check(c)!!.code)
 }
 private fun seed(c:Application){
  c.getSharedPreferences("eco_metadata_notice_v1",0).edit().clear().putLong("attempt",System.currentTimeMillis()).putLong("code",9999).putString("name","9.9").commit()
 }
 @Test fun permissionDenialKeepsPopupDataWithoutNotification(){
  val c=RuntimeEnvironment.getApplication();seed(c)
  org.robolectric.Shadows.shadowOf(c).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
  assertNotNull(AutomaticUpdateNotice.check(c))
  assertTrue(c.getSystemService(android.app.NotificationManager::class.java).activeNotifications.isEmpty())
  assertEquals(0L,c.getSharedPreferences("eco_metadata_notice_v1",0).getLong("notified",0))
 }
 @Test fun blockedChannelIsNotMarkedNotified(){
  val c=RuntimeEnvironment.getApplication();seed(c)
  org.robolectric.Shadows.shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
  val manager=c.getSystemService(android.app.NotificationManager::class.java)
  manager.createNotificationChannel(android.app.NotificationChannel("eco_metadata_notice","App updates",android.app.NotificationManager.IMPORTANCE_NONE))
  assertNotNull(AutomaticUpdateNotice.check(c))
  assertTrue(manager.activeNotifications.isEmpty())
  assertEquals(0L,c.getSharedPreferences("eco_metadata_notice_v1",0).getLong("notified",0))
 }
 @Test fun notifiedVersionIsNotPostedAgain(){
  val c=RuntimeEnvironment.getApplication();seed(c)
  org.robolectric.Shadows.shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
  val manager=c.getSystemService(android.app.NotificationManager::class.java)
  AutomaticUpdateNotice.check(c)
  assertEquals(1,manager.activeNotifications.size)
  manager.cancelAll()
  AutomaticUpdateNotice.check(c)
  assertTrue(manager.activeNotifications.isEmpty())
 }
 @Test fun workerUsesDailyNetworkAndBatteryConstraints(){
  val spec=AutomaticUpdateNotice.request().workSpec
  assertEquals(AutomaticUpdateNotice.INTERVAL_MS,spec.intervalDuration)
  assertEquals(androidx.work.NetworkType.CONNECTED,spec.constraints.requiredNetworkType)
  assertTrue(spec.constraints.requiresBatteryNotLow())
  assertFalse(spec.constraints.requiresCharging())
 }
}
