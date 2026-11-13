package com.example
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.model.BluetoothObservationState
import com.example.ui.components.BluetoothObservationNote
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class BluetoothObservationRenderTest {
 @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
 private fun render(dark:Boolean) {
  rule.setContent { MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()) {
   Surface { Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    Text("Test render, not a live phone")
    Text("Bluetooth Device Sentinel",style=MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      FilterChip(selected=true,onClick={},label={Text("REPORTED NOW (0)")})
      FilterChip(selected=false,onClick={},label={Text("HISTORY (0)")})
    }
    Text("When observations are unavailable:",style=MaterialTheme.typography.titleMedium)
    BluetoothObservationNote(BluetoothObservationState.UNAVAILABLE)
    Text("When devices are reported:",style=MaterialTheme.typography.titleMedium)
    BluetoothObservationNote(BluetoothObservationState.OBSERVED)
    Text("When access is missing:",style=MaterialTheme.typography.titleMedium)
    BluetoothObservationNote(BluetoothObservationState.PERMISSION_REQUIRED)
   } }
  } }
  rule.waitForIdle();rule.runOnIdle {
   val v=rule.activity.window.decorView
   val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
   v.draw(android.graphics.Canvas(b))
   java.io.File("build/outputs/ui-renders/bluetooth-observation-${if(dark)"dark" else "light"}.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   b.recycle()
  }
 }
 @Test fun light()=render(false)
 @Test fun dark()=render(true)
}
