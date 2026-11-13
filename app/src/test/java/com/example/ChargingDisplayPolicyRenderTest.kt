package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.components.ChargingScreenCard
import com.example.ui.components.CHARGING_DISPLAY_POLICY_NOTE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class ChargingDisplayPolicyRenderTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private fun render(dark:Boolean) {
        rule.setContent { MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
            Surface { Column(Modifier.fillMaxSize()) {
                Text("Test render, not a live phone",Modifier.padding(12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) { ChargingScreenCard() }
            } }
        } }
        rule.onNodeWithText(CHARGING_DISPLAY_POLICY_NOTE).performScrollTo().assertIsDisplayed()
        rule.waitForIdle()
        rule.runOnIdle {
            val view=rule.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/ui-renders/charging-display-policy-${if(dark) "dark" else "light"}.png").apply {parentFile?.mkdirs()}.outputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            b.recycle()
        }
    }
    @Test fun lightPreview()=render(false)
    @Test fun darkPreview()=render(true)
}
