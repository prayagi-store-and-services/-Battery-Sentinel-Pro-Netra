package com.example

import android.os.BatteryManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.service.BatterySnapshot
import com.example.service.LivePowerTelemetry
import com.example.ui.components.LivePowerContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi", sdk = [34], application = android.app.Application::class)
class RestoredSessionDurationRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun render(dark: Boolean) {
        val telemetry = LivePowerTelemetry({ 1000000L }, { 8200000L })
        val ui = telemetry.update(BatterySnapshot(BatteryManager.BATTERY_STATUS_CHARGING, 1, 50, 100, 4000, 300, 1000000), 10000L)
        rule.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Surface { Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Test data: restored session, not a live phone", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(12.dp))
                LivePowerContent(ui)
            } }
        } }
        rule.waitForIdle()
        rule.runOnIdle {
            val v = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/outputs/ui-renders/restored-duration-${if (dark) "dark" else "light"}.png").apply { parentFile.mkdirs() }
                .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        rule.onNodeWithText("Session Duration: 02:00:00").assertIsDisplayed()
        rule.onNodeWithText("Estimated Time To Full: Calculating...").assertIsDisplayed()
    }
    @Test fun renderLight() = render(false)
    @Test fun renderDark() = render(true)
}
