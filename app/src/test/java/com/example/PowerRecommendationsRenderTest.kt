package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ui.components.PowerPreferencesBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34])
class PowerRecommendationsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun fiveLevelControlsVisibleAt360dp() {
        rule.setContent { MaterialTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { PowerPreferencesBody() } } } }
        rule.onNodeWithText("Battery-level suggestions").assertIsDisplayed()
        rule.onNodeWithText("Suggest at 50%").assertIsDisplayed()
        rule.onNodeWithText("Suggest at 5%").assertIsDisplayed()
        rule.runOnIdle {
            val v = rule.activity.window.decorView
            val b = android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/power-recommendations-fixture.png").apply { parentFile?.mkdirs() }.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            b.recycle()
        }
    }
}
