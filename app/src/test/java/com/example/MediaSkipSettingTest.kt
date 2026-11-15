package com.example

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepository
import com.example.service.AnnouncementEngine
import com.example.service.AnnouncementItem
import com.example.service.AnnouncementMediaGate
import com.example.service.AnnouncementPriority
import com.example.service.AnnouncementSpeechState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaSkipSettingTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun prefs() = context.getSharedPreferences("netra_sentinel_prefs", Context.MODE_PRIVATE)

    @Test fun explicitOffAllowsAdmissionWithoutAudioFocus() {
        prefs().edit().clear().putBoolean("skip_announcements_during_media", false).commit()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(true)
        val engine = AnnouncementEngine(context)
        try {
            assertFalse(AnnouncementMediaGate.shouldSkip(context))
            val item = AnnouncementItem("setting-off", "Test", AnnouncementPriority.INFORMATIONAL,
                "DIRECT_TEST", isNightException = true)
            engine.enqueue(item)
            assertEquals(AnnouncementSpeechState.QUEUED, item.speechState)
            assertNull(shadowOf(audio).lastAudioFocusRequest)
        } finally { engine.shutdown(); shadowOf(audio).setIsMusicActive(false); prefs().edit().clear().commit() }
    }

    @Test fun settingPersistsAndChangesSharedGateImmediately() {
        prefs().edit().clear().commit()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(true)
        try {
            val repo = SettingsRepository(context)
            assertTrue(repo.settings.value.skipAnnouncementsDuringMedia)
            repo.setSkipAnnouncementsDuringMedia(false)
            assertFalse(repo.settings.value.skipAnnouncementsDuringMedia)
            assertFalse(SettingsRepository(context).settings.value.skipAnnouncementsDuringMedia)
            assertFalse(AnnouncementMediaGate.shouldSkip(context))
            repo.setSkipAnnouncementsDuringMedia(true)
            assertTrue(AnnouncementMediaGate.shouldSkip(context))
            assertTrue(SettingsRepository(context).settings.value.skipAnnouncementsDuringMedia)
            assertFalse(prefs().contains("media_playback_handling"))
        } finally { shadowOf(audio).setIsMusicActive(false); prefs().edit().clear().commit() }
    }

    @Test fun disabledBypassesPublicPlaybackDetectorToo() {
        prefs().edit().clear().putBoolean(SettingsRepository.MEDIA_SKIP_KEY, false).commit()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(false)
        shadowOf(audio).setActivePlaybackConfigurationsFor(listOf(
            android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()))
        try { assertFalse(AnnouncementMediaGate.shouldSkip(context)) }
        finally { shadowOf(audio).setActivePlaybackConfigurationsFor(emptyList()); prefs().edit().clear().commit() }
    }

    @Test fun oldOffDoesNotChangeNewDefaultOn() {
        prefs().edit().clear().putBoolean("media_playback_handling", false).commit()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(true)
        try { assertTrue(AnnouncementMediaGate.shouldSkip(context)) }
        finally { shadowOf(audio).setIsMusicActive(false); prefs().edit().clear().commit() }
    }
}
