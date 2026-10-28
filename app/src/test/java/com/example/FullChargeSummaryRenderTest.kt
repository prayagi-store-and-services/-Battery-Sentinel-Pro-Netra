package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.service.FullChargeObservation
import com.example.service.FullChargeSummaryStore
import com.example.ui.screens.FullChargeSummaryCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class FullChargeSummaryRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun render(dark: Boolean) {
        val value = FullChargeSummaryStore.Completed(1_793_174_400_000,
            FullChargeObservation.Summary(1_793_174_280_000, 90, 300_000, 120_000))
        rule.setContent { MaterialTheme(colorScheme = if(dark) darkColorScheme() else lightColorScheme()) {
            Surface { Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("UI fixture - sample data, not a live phone", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(12.dp))
                FullChargeSummaryCard(value)
            } }
        } }
        rule.onNodeWithText("Last observed full-charge session").assertIsDisplayed()
        rule.onNodeWithText("Connected after first observed 100%: 2 min").assertIsDisplayed()
        rule.onNodeWithText("Observed intervals, not exact physical completion time. Gaps over 15 minutes, unknown readings and app restarts break the observation window. No battery-damage estimate.").assertIsDisplayed()
        rule.runOnIdle {
            val v = rule.activity.window.decorView
            val b = android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/ui-renders/full-charge-${if(dark) "dark" else "light"}.png").apply { parentFile.mkdirs() }.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            b.recycle()
        }
    }
    @Test fun renderLight() = render(false)
    @Test fun renderDark() = render(true)
}
