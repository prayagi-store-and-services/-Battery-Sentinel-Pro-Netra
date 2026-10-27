package com.example
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.update.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=Application::class)
class EcoUpdateNoticeScreenshotTest {
 @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
 @Test fun newerVersionShowsUpdateWithoutDownloadChoice(){
  rule.setContent{MaterialTheme{UpdateCheckCard(metadataLoader={AppUpdater.Newer(999,"9.9 (fixture)")})}}
  rule.waitUntil(10000){rule.onAllNodesWithText("Update").fetchSemanticsNodes().isNotEmpty()}
  rule.onNodeWithText("Update").assertIsDisplayed()
  rule.onAllNodesWithText("Download").assertCountEquals(0)
  rule.runOnIdle{
   val v=rule.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b))
   java.io.File("build/outputs/metadata-update-notice.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()
  }
 }
 @Test fun noBrowserShowsFailureWithoutDownload(){
  rule.setContent{MaterialTheme{UpdateCheckCard(metadataLoader={AppUpdater.Newer(999,"9.9")})}}
  rule.waitUntil(10000){rule.onAllNodesWithText("Update").fetchSemanticsNodes().isNotEmpty()}
  rule.onNodeWithText("Update").performClick()
  rule.onNodeWithText("Could not open Eco or a browser. Try again.").assertIsDisplayed()
 }
 @Test fun dismissedPopupStaysDismissedAfterStateRestoration(){
  val restore=androidx.compose.ui.test.junit4.StateRestorationTester(rule)
  restore.setContent{MaterialTheme{AppUpdatePrompt(metadataLoader={AppUpdater.Newer(999,"9.9")})}}
  rule.waitUntil(10000){rule.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()}
  rule.onNodeWithText("Later").performClick()
  restore.emulateSavedInstanceStateRestore()
  rule.waitForIdle()
  rule.onAllNodesWithText("Later").assertCountEquals(0)
  rule.onAllNodesWithText("Update").assertCountEquals(0)
 }
}
