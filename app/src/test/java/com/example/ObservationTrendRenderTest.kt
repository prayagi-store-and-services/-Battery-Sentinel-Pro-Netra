package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ai.*
import com.example.ui.components.RecordedObservationTrends
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class ObservationTrendRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun sparseAndCoveredObservationsHaveHonestLabels() {
        val now = 100L * ObservationTrendPolicy.DAY_MS
        val days = (86L..99L).map { DailyBatteryObservation(it,12,if(it<93)35.0 else 37.0) }
        val reports = listOf(ObservationTrendPolicy.compare(days,now,7),ObservationTrendPolicy.compare(days,now,30))
        rule.setContent { MaterialTheme { Surface { Column(Modifier.fillMaxSize().padding(16.dp)) { RecordedObservationTrends(reports) } } } }
        rule.onNodeWithText("Weekly recorded temperature: Enough days recorded (limited)").assertIsDisplayed()
        rule.onNodeWithText("Monthly recorded temperature: Not enough days recorded").assertIsDisplayed()
        rule.onNodeWithText("Temperature comparison unavailable").assertIsDisplayed()
        rule.runOnIdle {
            val v = rule.activity.window.decorView
            val b = android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/ui-renders/observation-trend-coverage.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            b.recycle()
        }
    }
}
