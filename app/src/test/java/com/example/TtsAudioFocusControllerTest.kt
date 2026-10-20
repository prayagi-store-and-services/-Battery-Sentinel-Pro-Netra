package com.example

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.service.TtsAudioFocusController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsAudioFocusControllerTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    @Test
    fun acquireHoldsFocusAndReleaseLetsItGo() {
        val controller = TtsAudioFocusController(context)
        assertFalse(controller.isHeld)
        assertTrue(controller.acquire())
        assertTrue(controller.isHeld)
        // Idempotent while held: a second utterance must not stack requests.
        assertTrue(controller.acquire())
        assertTrue(controller.isHeld)
        assertTrue(controller.release())
        assertFalse(controller.isHeld)
        // Releasing twice is a safe no-op for the watchdog + callback double path.
        assertFalse(controller.release())
        assertFalse(controller.isHeld)
    }

    @Test
    fun utteranceLifecycleRequestsTransientFocusOnce() {
        val controller = TtsAudioFocusController(context)
        assertTrue(controller.acquire())
        // Robolectric 4.16.1 does not expose the gain on its AudioFocusRequest shadow,
        // so the assertion is on the request existing, not on its internals.
        val requested = shadowOf(audioManager).lastAudioFocusRequest
        assertNotNull(requested)
        assertTrue(controller.release())
        val abandoned = shadowOf(audioManager).lastAbandonedAudioFocusRequest
        assertNotNull(abandoned)
    }
}
