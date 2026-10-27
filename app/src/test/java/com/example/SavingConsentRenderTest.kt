package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ui.components.SavingConsentCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=Application::class)
class SavingConsentRenderTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun defaultOffConsentAndUndoAreVisible() {
        rule.setContent { MaterialTheme { Surface(Modifier.fillMaxSize().padding(16.dp)) { SavingConsentCard() } } }
        rule.onNodeWithText("Allow brightness and screen timeout").assertIsDisplayed()
        rule.onNodeWithText("Allow pausing global auto-sync").assertIsDisplayed()
        rule.onNodeWithText("Undo display/sync changes and stop display/sync changes").assertIsDisplayed()
        rule.runOnIdle {
            val v=rule.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/saving-consent-fixture.png").apply { parentFile?.mkdirs() }.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            b.recycle()
        }
    }
}
