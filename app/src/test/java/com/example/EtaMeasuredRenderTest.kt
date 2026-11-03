package com.example

import android.os.BatteryManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
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

/** Synthetic measured-input fixtures, not a claim of physical-phone validation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], application=android.app.Application::class, qualifiers="w411dp-h891dp-mdpi")
class EtaMeasuredRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun render(charging: Boolean) {
        val t = LivePowerTelemetry()
        fun snap(level: Int) = BatterySnapshot(if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING,
            if (charging) 1 else 0, level, 100, 4257, 383, if (charging) 4270 else -300000)
        for (sec in 0..600) t.update(snap(if (charging) 60 + sec/300 else 64 - sec/300), sec * 1000L)
        val ui = t.update(snap(62), 600000L)
        rule.setContent { MaterialTheme { Surface { Column(Modifier.width(380.dp).padding(16.dp)) { LivePowerContent(ui) } } } }
        rule.runOnIdle {
            val v=rule.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/ui-renders/eta-measured-" + (if (charging) "charging" else "discharging") + ".png").apply { parentFile?.mkdirs() }.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            b.recycle()
        }
        rule.onNodeWithText(if (charging) "~03:10:00 (est.)" else "~05:10:00 (est.)", substring=true).assertIsDisplayed()
    }
    @Test fun charging() = render(true)
    @Test fun discharging() = render(false)
}
