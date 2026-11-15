package com.example

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.service.AnnouncementEngine
import com.example.service.AnnouncementItem
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
class MediaSkipAnnouncementTest {
    @Test fun mediaSkipsEvenCriticalSpeechWithoutQueueOrAudioFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(true)
        val engine = AnnouncementEngine(context)
        val item = AnnouncementItem("media-critical", "Critical heat", AnnouncementPriority.CRITICAL_THERMAL,
            "DIRECT_TEST", isNightException = true)
        try {
            engine.enqueue(item)
            assertEquals(AnnouncementSpeechState.CANCELLED, item.speechState)
            val field = AnnouncementEngine::class.java.getDeclaredField("queue").apply { isAccessible = true }
            assertTrue((field.get(engine) as java.util.Queue<*>).isEmpty())
            assertNull(shadowOf(audio).lastAudioFocusRequest)
        } finally { engine.shutdown(); shadowOf(audio).setIsMusicActive(false) }
    }

    @Test fun mediaStartingAfterQueueingDiscardsPendingWithoutCatchup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val engine = AnnouncementEngine(context)
        shadowOf(audio).setIsMusicActive(false)
        val item = AnnouncementItem("pending", "Pending alert", AnnouncementPriority.PHONE_BATTERY,
            "DIRECT_TEST", isNightException = true)
        try {
            engine.enqueue(item)
            val field = AnnouncementEngine::class.java.getDeclaredField("queue").apply { isAccessible = true }
            assertTrue((field.get(engine) as java.util.Queue<*>).contains(item))
            shadowOf(audio).setIsMusicActive(true)
            AnnouncementEngine::class.java.getDeclaredMethod("processQueue").apply { isAccessible = true }.invoke(engine)
            assertEquals(AnnouncementSpeechState.CANCELLED, item.speechState)
            assertTrue((field.get(engine) as java.util.Queue<*>).isEmpty())
            shadowOf(audio).setIsMusicActive(false)
            AnnouncementEngine::class.java.getDeclaredMethod("processQueue").apply { isAccessible = true }.invoke(engine)
            assertTrue((field.get(engine) as java.util.Queue<*>).isEmpty())
            assertNull(shadowOf(audio).lastAudioFocusRequest)
        } finally { engine.shutdown(); shadowOf(audio).setIsMusicActive(false) }
    }

    @Test fun legacyMediaHandlingSwitchCannotBypassSkip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("netra_sentinel_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("media_playback_handling", false).apply()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(true)
        val engine = AnnouncementEngine(context)
        val item = AnnouncementItem("manual-test", "Manual test", AnnouncementPriority.INFORMATIONAL,
            "DIRECT_TEST", isNightException = true)
        try {
            engine.enqueue(item)
            assertEquals(AnnouncementSpeechState.CANCELLED, item.speechState)
        } finally { engine.shutdown(); shadowOf(audio).setIsMusicActive(false) }
    }

    @Test fun eitherDetectorBlocksAndFailureIsConservative() {
        assertTrue(com.example.service.AnnouncementMediaGate.evaluate({ true }, { false }))
        assertTrue(com.example.service.AnnouncementMediaGate.evaluate({ false }, { true }))
        assertFalse(com.example.service.AnnouncementMediaGate.evaluate({ false }, { false }))
        assertTrue(com.example.service.AnnouncementMediaGate.evaluate({ throw SecurityException() }, { false }))
        assertTrue(com.example.service.AnnouncementMediaGate.evaluate({ false }, { throw IllegalStateException() }))
    }

    @Test fun activePlaybackConfigurationBlocksWithoutMusicFlag() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(false)
        shadowOf(audio).setActivePlaybackConfigurationsFor(listOf(
            android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()))
        try { assertTrue(com.example.service.AnnouncementMediaGate.shouldSkip(context)) }
        finally { shadowOf(audio).setActivePlaybackConfigurationsFor(emptyList()) }
    }

    @Test @Config(sdk = [24]) fun api24UsesMusicDetectorWithoutNewApi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(true)
        try { assertTrue(com.example.service.AnnouncementMediaGate.shouldSkip(context)) }
        finally { shadowOf(audio).setIsMusicActive(false) }
    }
}
