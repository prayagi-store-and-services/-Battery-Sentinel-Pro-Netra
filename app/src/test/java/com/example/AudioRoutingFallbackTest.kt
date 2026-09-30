package com.example

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepository
import com.example.model.AudioRouteType
import com.example.model.AudioRoutingPolicy
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.service.CentralCapabilityRegistry
import com.example.util.AudioRoutingInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioRoutingFallbackTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var capabilityRegistry: CentralCapabilityRegistry

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        settingsRepository = SettingsRepository(context)
        capabilityRegistry = CentralCapabilityRegistry(context)
    }

    @Test
    fun `audio routing inspector detects basic speaker capability and documents limitation`() {
        val status = AudioRoutingInspector.inspectRouting(context, AudioRoutingPolicy.AUTO_BT_WITH_SPEAKER_FALLBACK)

        assertTrue("Should have built-in speaker", status.hasBuiltInSpeaker)
        assertFalse("Standard AOSP does not support simultaneous dual-sink playback", status.isSimultaneousDualOutputSupported)
        assertNotNull("Limitation details must be documented", status.limitationDetails)
        assertTrue(
            "Limitation details should explain AudioPolicyManager/HAL single sink constraint",
            status.limitationDetails.contains("AudioPolicyManager") || status.limitationDetails.contains("AOSP")
        )
    }

    @Test
    fun `simultaneous playback check returns false when no vendor dual audio exists`() {
        val isSupported = AudioRoutingInspector.checkSimultaneousDualPlaybackSupported(
            audioManager = audioManager,
            isBtA2dp = true,
            hasSpeaker = true
        )
        assertFalse("AOSP single-sink constraint should yield false without vendor dual audio", isSupported)
    }

    @Test
    fun `fallback triggers on Bluetooth TTS failure or muted volume`() {
        val btStatus = AudioRoutingInspector.inspectRouting(context, AudioRoutingPolicy.AUTO_BT_WITH_SPEAKER_FALLBACK).copy(
            isBluetoothA2dpConnected = true,
            isMusicVolumeAdequate = false // muted
        )

        // Muted BT volume should trigger speaker fallback
        val shouldFallbackMuted = AudioRoutingInspector.shouldTriggerSpeakerFallback(
            status = btStatus,
            isCriticalSafetyAlert = false,
            ttsDispatchSuccess = true
        )
        assertTrue("Muted Bluetooth volume must trigger speaker fallback", shouldFallbackMuted)

        // TTS dispatch failure should trigger speaker fallback
        val shouldFallbackTtsFailure = AudioRoutingInspector.shouldTriggerSpeakerFallback(
            status = btStatus.copy(isMusicVolumeAdequate = true),
            isCriticalSafetyAlert = false,
            ttsDispatchSuccess = false
        )
        assertTrue("TTS failure on peripheral must trigger speaker fallback", shouldFallbackTtsFailure)
    }

    @Test
    fun `forced speaker policy bypasses Bluetooth and directs to phone speaker`() {
        val status = AudioRoutingInspector.inspectRouting(context, AudioRoutingPolicy.FORCE_PHONE_SPEAKER).copy(
            isBluetoothA2dpConnected = true
        )

        assertEquals(AudioRouteType.BUILTIN_SPEAKER, status.activePrimaryRoute)

        val shouldFallback = AudioRoutingInspector.shouldTriggerSpeakerFallback(
            status = status,
            isCriticalSafetyAlert = true,
            ttsDispatchSuccess = true
        )
        assertFalse("Forced speaker mode already uses speaker, no fallback needed", shouldFallback)
    }

    @Test
    fun `dual sequential policy triggers companion speaker chime on critical safety alert`() {
        val status = AudioRoutingInspector.inspectRouting(context, AudioRoutingPolicy.DUAL_ATTEMPT_SEQUENTIAL).copy(
            isBluetoothA2dpConnected = true,
            isMusicVolumeAdequate = true
        )

        val shouldFallbackForCritical = AudioRoutingInspector.shouldTriggerSpeakerFallback(
            status = status,
            isCriticalSafetyAlert = true,
            ttsDispatchSuccess = true
        )
        assertTrue("Critical safety alert in dual mode must trigger companion speaker delivery", shouldFallbackForCritical)

        val shouldFallbackForNormal = AudioRoutingInspector.shouldTriggerSpeakerFallback(
            status = status,
            isCriticalSafetyAlert = false,
            ttsDispatchSuccess = true
        )
        assertFalse("Normal non-critical alert in dual mode does not require companion speaker chime", shouldFallbackForNormal)
    }

    @Test
    fun `settings repository persists audio routing policy correctly`() {
        settingsRepository.setAudioRoutingPolicy(AudioRoutingPolicy.FORCE_PHONE_SPEAKER)
        assertEquals(AudioRoutingPolicy.FORCE_PHONE_SPEAKER, settingsRepository.settings.value.audioRoutingPolicy)

        settingsRepository.setAudioRoutingPolicy(AudioRoutingPolicy.DUAL_ATTEMPT_SEQUENTIAL)
        assertEquals(AudioRoutingPolicy.DUAL_ATTEMPT_SEQUENTIAL, settingsRepository.settings.value.audioRoutingPolicy)

        settingsRepository.setAudioRoutingPolicy(AudioRoutingPolicy.AUTO_BT_WITH_SPEAKER_FALLBACK)
        assertEquals(AudioRoutingPolicy.AUTO_BT_WITH_SPEAKER_FALLBACK, settingsRepository.settings.value.audioRoutingPolicy)
    }

    @Test
    fun `capability registry recognizes audio routing and fallback capability`() {
        val capabilities = capabilityRegistry.detectAllCapabilities()
        assertEquals(CapabilityStatus.AVAILABLE, capabilities[CapabilityType.AUDIO_ROUTING_FALLBACK])
        assertEquals("Audio Routing & Fallback Engine", capabilityRegistry.getCapabilityLabel(CapabilityType.AUDIO_ROUTING_FALLBACK))
    }
}
