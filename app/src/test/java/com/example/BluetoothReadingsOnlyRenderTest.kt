package com.example
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.components.BluetoothReadingsOnlyNote
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h360dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class BluetoothReadingsOnlyRenderTest {
 @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
 private fun render(dark:Boolean){
 rule.setContent{MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()){
 Surface{Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
 Text("Settings change preview",style=MaterialTheme.typography.titleLarge)
 Text("Test component render, not a live phone",style=MaterialTheme.typography.bodySmall)
 Text("Announcement settings",style=MaterialTheme.typography.titleMedium)
 BluetoothReadingsOnlyNote()
 Text("The old Bluetooth announcement switch is removed. Devices readings and ordinary phone/thermal audio routing are unchanged.",style=MaterialTheme.typography.bodySmall)
 }}}}
 rule.waitForIdle();rule.runOnIdle{
 val v=rule.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b))
 java.io.File("build/outputs/ui-renders/bluetooth-readings-only-${if(dark)"dark" else "light"}.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()
 }}
 @Test fun light()=render(false)
 @Test fun dark()=render(true)
}
