package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.screens.BatteryElectricalPowerCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class ChargingEnergyRenderTest {
  @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
  private fun render(missing:Boolean) {
    val state=if(missing) NetraCentralState(isCharging=true,isChargerConnected=true) else NetraCentralState(
      isCharging=true,isChargerConnected=true,voltageMv=4000,currentMa=-2000,powerWatts=8f,
      chargingSpeed=CanonicalChargingSpeed.NORMAL,announcementSpeed=CanonicalChargingSpeed.NORMAL,
      estimatedStoredEnergyRateWatts=9f)
    rule.setContent { MaterialTheme(colorScheme=lightColorScheme()) { Surface { Column(Modifier.fillMaxSize().padding(16.dp)) {
      Text("Automated UI fixture, not a phone measurement",style=MaterialTheme.typography.labelSmall)
      Spacer(Modifier.height(12.dp))
      BatteryElectricalPowerCard(state)
    } } } }
    rule.onNodeWithText("Net stored-energy rate (estimated):",substring=true).assertIsDisplayed()
    if(missing) rule.onAllNodesWithText("Unavailable",substring=true).assertCountEquals(3)
    else rule.onNodeWithText("Normal (5W–<10W)").assertIsDisplayed()
    rule.runOnIdle {
      val v=rule.activity.window.decorView
      val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
      v.draw(android.graphics.Canvas(b))
      java.io.File("build/outputs/ui-renders/energy-counter-${if(missing) "unavailable" else "stable"}.png").apply{parentFile.mkdirs()}.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
      b.recycle()
    }
  }
  @Test fun stableRateKeepsSensorPowerIndependent()=render(false)
  @Test fun missingEnergyNeverDisplaysNormal()=render(true)
}
