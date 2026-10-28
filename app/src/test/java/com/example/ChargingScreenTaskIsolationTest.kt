package com.example
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.ChargingScreenActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ChargingScreenTaskIsolationTest {
 @Test fun backgroundScreenCannotReuseMainTask() {
  val c:Context=ApplicationProvider.getApplicationContext()
  val info=c.packageManager.getActivityInfo(ComponentName(c,ChargingScreenActivity::class.java),0)
  val main=c.packageManager.getActivityInfo(ComponentName(c,MainActivity::class.java),0)
  assertNotEquals(main.taskAffinity,info.taskAffinity)
  assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
  assertEquals(0,info.flags and ActivityInfo.FLAG_ALLOW_TASK_REPARENTING)
  assertFalse(info.exported)
 }
}
