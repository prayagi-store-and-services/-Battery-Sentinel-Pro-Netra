package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.repository.SettingsRepository
import com.example.service.AnnouncementMediaGate
import com.example.ui.components.MediaPlaybackSkipCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=android.app.Application::class)
class MediaSkipSettingRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun render(dark: Boolean, initial: Boolean) {
        val context = rule.activity
        val prefs = context.getSharedPreferences("netra_sentinel_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean(SettingsRepository.MEDIA_SKIP_KEY, initial).commit()
        val repo = SettingsRepository(context)
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        shadowOf(audio).setIsMusicActive(true)
        try {
            rule.setContent { MaterialTheme(colorScheme = if(dark) darkColorScheme() else lightColorScheme()) {
                val settings by repo.settings.collectAsState()
                Surface { Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Test render, not a live phone", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(12.dp))
                    MediaPlaybackSkipCard(settings.skipAnnouncementsDuringMedia, repo::setSkipAnnouncementsDuringMedia)
                } }
            } }
            rule.onNodeWithText("Skip during media playback").assertIsDisplayed()
            if(initial) rule.onNodeWithTag("media_skip_switch").assertIsOn()
            else rule.onNodeWithTag("media_skip_switch").assertIsOff()
            rule.runOnIdle {
                assertEquals(initial, AnnouncementMediaGate.shouldSkip(context))
                val v = rule.activity.window.decorView
                val b = android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(b))
                java.io.File("build/outputs/ui-renders/media-skip-${if(initial) "on" else "off"}-${if(dark) "dark" else "light"}.png").apply { parentFile.mkdirs() }.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                b.recycle()
            }
            rule.onNodeWithTag("media_skip_switch").performClick()
            rule.runOnIdle { assertEquals(!initial, AnnouncementMediaGate.shouldSkip(context)) }
            if(initial) rule.onNodeWithTag("media_skip_switch").assertIsOff()
            else rule.onNodeWithTag("media_skip_switch").assertIsOn()
        } finally { shadowOf(audio).setIsMusicActive(false); prefs.edit().clear().commit() }
    }
    @Test fun onLight() = render(false, true)
    @Test fun onDark() = render(true, true)
    @Test fun offLight() = render(false, false)
    @Test fun offDark() = render(true, false)
}
