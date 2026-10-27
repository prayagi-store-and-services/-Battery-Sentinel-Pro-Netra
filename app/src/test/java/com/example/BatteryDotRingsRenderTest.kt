package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ui.components.BatteryDotRings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34])
class BatteryDotRingsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun measuredFixtureAndUnavailableCore() {
        rule.setContent { MaterialTheme { Surface { Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Local fixture, not device readings")
            BatteryDotRings(64, 39.7f, true, true, Modifier.width(300.dp))
            BatteryDotRings(null, null, false, true, Modifier.width(240.dp))
        } } } }
        rule.onNodeWithText("64%").assertIsDisplayed()
        rule.onAllNodesWithText("Unavailable").assertCountEquals(4)
        rule.runOnIdle {
            val v=rule.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/ui-renders/battery-dot-rings.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            b.recycle()
        }
    }
    @Test fun finiteSampleMotionAndScreenOffStopsIt() {
        val power = rule.activity.getSystemService(android.os.PowerManager::class.java)
        org.robolectric.Shadows.shadowOf(power).setIsInteractive(true)
        org.robolectric.Shadows.shadowOf(power).setIsPowerSaveMode(false)
        val level = mutableStateOf(64)
        val screen = mutableStateOf(true)
        rule.setContent { MaterialTheme { Surface { BatteryDotRings(level.value,37f,true,screen.value,Modifier.width(300.dp)) } } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { level.value = 65 } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(16)
        rule.mainClock.advanceTimeBy(160)
        rule.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Moving blue dots")).assertExists()
        capture("motion-in-160")
        rule.mainClock.advanceTimeBy(160)
        capture("motion-in-320")
        rule.mainClock.advanceTimeBy(600)
        capture("motion-in-settled")
        rule.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Settled dots")).assertExists()
        rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { level.value = 64 } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(160)
        capture("motion-out-160")
        rule.mainClock.advanceTimeBy(160)
        capture("motion-out-320")
        rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { screen.value = false } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(32)
        rule.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Settled dots")).assertExists()
        rule.mainClock.autoAdvance = true
    }

    private fun capture(name: String) {
        rule.runOnIdle {
            val v = rule.activity.window.decorView
            val b = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/ui-renders/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            b.recycle()
        }
    }

    @Test fun staleOffscreenAndPowerSaveStopActualMotion() {
        val power = rule.activity.getSystemService(android.os.PowerManager::class.java)
        org.robolectric.Shadows.shadowOf(power).setIsInteractive(true)
        org.robolectric.Shadows.shadowOf(power).setIsPowerSaveMode(false)
        val level = mutableStateOf(50)
        val temperature = mutableStateOf(37f)
        val fresh = mutableStateOf(true)
        val offscreen = mutableStateOf(false)
        rule.setContent { MaterialTheme { Surface { BatteryDotRings(level.value, temperature.value, fresh.value, true,
            Modifier.width(300.dp).offset(y = if (offscreen.value) 2000.dp else 0.dp)) } } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val moving = SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Moving blue dots")
        val settled = SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Settled dots")
        fun update(action: () -> Unit) {
            rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot(action) }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(64)
        }
        update { temperature.value = 38f }
        rule.onNode(moving).assertExists()
        capture("thermal-in")
        update { fresh.value = false }
        rule.onNode(settled).assertExists()
        update { level.value = 51 }
        rule.onNode(settled).assertExists()
        update { fresh.value = true }
        rule.onNode(settled).assertExists() // enabling must not replay retained samples
        update { temperature.value = 37f }
        rule.onNode(moving).assertExists()
        capture("thermal-out")
        update { offscreen.value = true }
        rule.onNode(settled).assertExists()
        update { level.value = 52 }
        rule.onNode(settled).assertExists()
        update { offscreen.value = false }
        rule.onNode(settled).assertExists()
        update { level.value = 53 }
        rule.onNode(moving).assertExists()
        org.robolectric.Shadows.shadowOf(power).setIsPowerSaveMode(true)
        rule.activity.sendBroadcast(android.content.Intent(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(64)
        rule.onNode(settled).assertExists()
        update { level.value = 54 }
        rule.onNode(settled).assertExists()
        rule.mainClock.autoAdvance = true
    }

    @Test fun ringDefaultAndExplicitLegacyChoicePreserveControls() {
        val c = rule.activity
        val settings = com.example.ui.components.ChargingStyleSettings
        val p = c.getSharedPreferences(settings.PREFS, android.content.Context.MODE_PRIVATE)
        p.edit().clear().putInt("gauge", 2).putInt("color", 4).putFloat("gaugeBrightness", .6f).commit()
        val loaded = settings.load(c)
        org.junit.Assert.assertEquals(6, loaded.gauge)
        org.junit.Assert.assertEquals(4, loaded.color)
        org.junit.Assert.assertEquals(.6f, loaded.gaugeBrightness, .001f)
        org.junit.Assert.assertEquals(2, p.getInt("gauge", -1)) // old value not deleted
        settings.save(c, loaded.copy(gauge = 2))
        org.junit.Assert.assertEquals(2, settings.load(c).gauge)
        org.junit.Assert.assertEquals(4, settings.load(c).color)
        p.edit().clear().commit()
    }

    @Test fun chargingScreenDefaultRingsRetainsPreviewControls() {
        rule.activity.getSharedPreferences(com.example.ui.components.ChargingStyleSettings.PREFS, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        rule.setContent { MaterialTheme { com.example.ui.components.ChargingScreenContent({}, preview = true) } }
        rule.waitForIdle()
        rule.onNodeWithText("Health").assertIsDisplayed()
        rule.onNodeWithText("Power is battery-side (voltage x current), not wall-adapter wattage. Double tap to close.").assertIsDisplayed()
        capture("charging-screen-rings")
    }

}
