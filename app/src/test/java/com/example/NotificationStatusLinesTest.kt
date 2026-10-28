package com.example
import android.app.Application
import android.graphics.Color
import com.example.model.NetraCentralState
import com.example.service.NotificationStatusLines
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], application=Application::class)
class NotificationStatusLinesTest {
 @Test fun staticCacheAndMeasuredFill() {
  val a=NotificationStatusLines.Values(78,36f,true)
  assertSame(NotificationStatusLines.bitmap(a),NotificationStatusLines.bitmap(a))
  assertNotSame(NotificationStatusLines.bitmap(a),NotificationStatusLines.bitmap(a.copy(battery=77)))
  val bitmap=NotificationStatusLines.bitmap(a)
  assertEquals(Color.rgb(53,200,144),bitmap.getPixel(100,16))
  assertNotEquals(Color.rgb(53,200,144),bitmap.getPixel(200,16))
  assertNotEquals(Color.rgb(53,200,144),bitmap.getPixel(100,80))
 }
 @Test fun staleAndInvalidCannotPretendLive() {
  val v=NotificationStatusLines.values(NetraCentralState(batteryLevel=80,temperatureCelsius=36f,isDataFresh=false))
  assertNull(v.battery);assertNull(v.temp);assertTrue(v.text.contains("Unavailable"))
  val bad=NotificationStatusLines.values(NetraCentralState(batteryLevel=101,temperatureCelsius=Float.NaN,isDataFresh=true))
  assertNull(bad.battery);assertNull(bad.temp)
 }
 @Test fun exactChargingScreenColourThresholds() {
  assertEquals(Color.rgb(240,68,82),NotificationStatusLines.color(15f,false))
  assertEquals(Color.rgb(244,186,61),NotificationStatusLines.color(30f,false))
  assertEquals(Color.rgb(53,200,144),NotificationStatusLines.color(31f,false))
  assertEquals(Color.rgb(244,186,61),NotificationStatusLines.color(38f,true))
  assertEquals(Color.rgb(240,68,82),NotificationStatusLines.color(39.5f,true))
 }
}
