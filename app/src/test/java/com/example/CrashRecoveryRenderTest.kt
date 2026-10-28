package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ui.components.CrashRecoveryCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class CrashRecoveryRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun pausedMonitoringIsLoudAndRetryVisible360dp() {
        rule.setContent { MaterialTheme { Surface { Column(Modifier.fillMaxSize()) {
            CrashRecoveryCard(1793147400000,null,{})
        } } } }
        rule.onNodeWithText("Monitoring paused after repeated crashes").assertIsDisplayed()
        rule.onNodeWithText("Retry monitoring now").assertIsDisplayed()
        rule.runOnIdle {
            val v=rule.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/ui-renders/crash-recovery-paused.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            b.recycle()
        }
    }
}
