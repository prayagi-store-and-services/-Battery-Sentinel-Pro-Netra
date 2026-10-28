package com.example

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ContextThemeWrapper
import com.example.model.NetraCentralState
import com.example.service.NotificationStatusLines
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class NotificationLinesLayoutTest {
 @Test fun largeFontCompactRemainsBoundedAndAccessible() {
  val base:Context=ApplicationProvider.getApplicationContext()
  val config=Configuration(base.resources.configuration).apply{fontScale=2f}
  val c=ContextThemeWrapper(base.createConfigurationContext(config),android.R.style.Theme_DeviceDefault_DayNight)
  val v=NotificationStatusLines.views(c,NetraCentralState(),"Last-known battery data - live update unavailable",false).apply(c,null)
  v.measure(View.MeasureSpec.makeMeasureSpec(288,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY));v.layout(0,0,288,48)
  val text=v.findViewById<android.widget.TextView>(R.id.status_values)
  assertTrue(text.contentDescription.toString().contains("Health Unavailable"))
  assertEquals(48,v.measuredHeight)
 }
 @Test fun renderActualRemoteViewsLightAndDark() {
  val base: Context=ApplicationProvider.getApplicationContext()
  for (dark in listOf(false,true)) {
   val config=Configuration(base.resources.configuration).apply{uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if(dark)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)}
   val c=ContextThemeWrapper(base.createConfigurationContext(config),android.R.style.Theme_DeviceDefault_DayNight)
   val bitmap=Bitmap.createBitmap(360,560,Bitmap.Config.ARGB_8888)
   val canvas=Canvas(bitmap);canvas.drawColor(if(dark)0xff252b33.toInt() else 0xffffffff.toInt())
   var y=8f
   for ((expanded,missing) in listOf(false to false,true to false,true to true)) {
    val state=NetraCentralState(batteryLevel=78,temperatureCelsius=if(missing)null else 36f,isDataFresh=true)
    val v=NotificationStatusLines.views(c,state,(if(missing)"78% - temperature unavailable" else "78% - 36.0°C - learning your phone's pattern"),expanded).apply(c,null)
    v.measure(View.MeasureSpec.makeMeasureSpec(328,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(252,View.MeasureSpec.AT_MOST))
    v.layout(0,0,v.measuredWidth,v.measuredHeight)
    assertTrue(v.measuredHeight<=252)
    if(!expanded) assertEquals(48,v.measuredHeight)
    canvas.save();canvas.translate(16f,y);v.draw(canvas);canvas.restore();y+=v.measuredHeight+24
   }
   java.io.File("build/outputs/ui-renders/notification-lines-${if(dark)"dark" else "light"}.png").apply{parentFile.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
  }
 }
}
