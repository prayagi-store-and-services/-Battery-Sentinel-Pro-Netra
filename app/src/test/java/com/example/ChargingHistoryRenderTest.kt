package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.local.ChargingSession
import com.example.ui.screens.ChargingSessionItemRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi", sdk = [34], application = android.app.Application::class)
class ChargingHistoryRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun render(dark: Boolean) {
        val session = ChargingSession(startTime=1_000_000L,endTime=1_020_000L,startLevel=70,endLevel=71,peakTemperature=31f,avgPowerWatts=4f,chargerType="AC",durationMinutes=0)
        rule.setContent { MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
            Surface { Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Test data, not a live phone")
                Text("Recorded Charging Intervals", style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp)); ChargingSessionItemRow(session)
            } }
        } }
        rule.waitForIdle()
        rule.onNodeWithText("Partial recorded interval").assertIsDisplayed()
        rule.onNodeWithText("<1 min").assertIsDisplayed()
        rule.onNodeWithText("Gaps and restarts can omit earlier readings.").assertIsDisplayed()
        rule.runOnIdle {
            val v=rule.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/outputs/ui-renders/charging-history-${if(dark) "dark" else "light"}.png").apply {parentFile?.mkdirs()}.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun lightPreview()=render(false)
    @Test fun darkPreview()=render(true)
}
