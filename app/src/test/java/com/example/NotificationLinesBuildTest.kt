package com.example
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NetraCentralState
import com.example.service.SentinelStatusNotification
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NotificationLinesBuildTest {
 @Test fun bothCustomLayoutsShareExistingSilentChannelAndPauseRemainsHonest() {
  val c:Context=ApplicationProvider.getApplicationContext()
  SentinelStatusNotification.ensureChannel(c)
  val n=SentinelStatusNotification.build(c,NetraCentralState(batteryLevel=78,temperatureCelsius=36f,isDataFresh=true))
  assertNotNull(n.contentView);assertNotNull(n.bigContentView)
  assertEquals(SentinelStatusNotification.CHANNEL_ID,n.channelId)
  assertNotEquals(0,n.flags and android.app.Notification.FLAG_ONGOING_EVENT)
  val paused=SentinelStatusNotification.buildPaused(c)
  assertNull(paused.contentView)
  assertTrue(paused.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains("Monitoring paused"))
 }
}
