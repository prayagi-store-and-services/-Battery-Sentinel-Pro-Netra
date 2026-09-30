package com.example.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.model.AudioRouteType
import com.example.model.AudioRoutingPolicy
import com.example.model.AudioRoutingStatus

/**
 * Audio Routing Inspector and Fallback Engine for Battery Sentinel Pro Netra.
 *
 * Investigates device audio output topology using [AudioManager] and [AudioDeviceInfo].
 *
 * TECHNICAL LIMITATION & ARCHITECTURAL DOCUMENTATION:
 * Under the standard Android Open Source Project (AOSP) audio framework and audio HAL
 * (Hardware Abstraction Layer), the [android.media.AudioPolicyManager] evaluates active
 * audio tracks and routes an active audio stream exclusively to a single primary sink
 * endpoint (e.g. Bluetooth A2DP when connected, or the built-in speaker).
 *
 * Concurrent dual-sink hardware playback of a single audio stream to BOTH the phone's
 * built-in speakers and an external Bluetooth A2DP accessory simultaneously is NOT supported
 * on standard Android public APIs without custom OEM-proprietary multi-sink HAL integration
 * (e.g. Samsung Dual Audio / Samsung SoundAssistant).
 *
 * NETRA FALLBACK POLICY:
 * To guarantee that critical voice announcements (overheat warnings, low battery alerts,
 * and charging speed updates) are never lost or silenced when Bluetooth peripherals are
 * connected:
 * 1. AUTO_BT_WITH_SPEAKER_FALLBACK: Routes primarily to the connected Bluetooth device,
 *    and immediately executes phone speaker fallback if Bluetooth stream is muted, volume is zero,
 *    or TTS speech dispatch reports an error.
 * 2. FORCE_PHONE_SPEAKER: Bypasses external Bluetooth sink and directs speech directly to
 *    the device's built-in loudspeaker.
 * 3. DUAL_ATTEMPT_SEQUENTIAL: Dispatches full speech to the primary Bluetooth device and
 *    emits an accompanying safety alert tone/chime over the device speaker for critical thermal events.
 */
object AudioRoutingInspector {

    private const val TAG = "NetraAudioRouting"

    const val LIMITATION_DOCUMENTATION =
        "Standard Android AOSP AudioPolicyManager and audio HAL route audio exclusively to a single active output sink (Bluetooth A2DP when connected). Concurrent hardware multi-sink playback to both internal speakers and external Bluetooth is not supported on standard Android without custom vendor HAL. Netra enforces an intelligent fallback and volume safeguard policy to guarantee all announcements remain audible."

    /**
     * Inspects active audio routing, hardware output sinks, and volume levels.
     */
    fun inspectRouting(
        context: Context,
        policy: AudioRoutingPolicy = AudioRoutingPolicy.AUTO_BT_WITH_SPEAKER_FALLBACK
    ): AudioRoutingStatus {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioRoutingStatus(
                hasBuiltInSpeaker = true,
                isBluetoothA2dpConnected = false,
                isBluetoothScoActive = false,
                isWiredHeadsetConnected = false,
                isSimultaneousDualOutputSupported = false,
                activePrimaryRoute = AudioRouteType.BUILTIN_SPEAKER,
                activePolicy = policy,
                isSpeakerVolumeAdequate = true,
                isMusicVolumeAdequate = true,
                limitationDetails = LIMITATION_DOCUMENTATION,
                lastFallbackTriggered = null
            )

        var hasSpeaker = true
        var isBtA2dp = false
        var isBtSco = false
        var isWired = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (device in devices) {
                    when (device.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
                            hasSpeaker = true
                        }
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                            isBtA2dp = true
                        }
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                            isBtSco = true
                        }
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_DEVICE,
                        AudioDeviceInfo.TYPE_USB_HEADSET -> {
                            isWired = true
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        ) {
                            isBtA2dp = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying AudioDeviceInfo outputs", e)
            }
        }

        // Fallback checks for older APIs or legacy manager state
        if (!isBtA2dp) {
            try {
                @Suppress("DEPRECATION")
                isBtA2dp = am.isBluetoothA2dpOn
            } catch (_: Exception) {}
        }
        if (!isBtSco) {
            try {
                @Suppress("DEPRECATION")
                isBtSco = am.isBluetoothScoOn
            } catch (_: Exception) {}
        }
        if (!isWired) {
            try {
                @Suppress("DEPRECATION")
                isWired = am.isWiredHeadsetOn
            } catch (_: Exception) {}
        }

        // Volume adequacy checks
        val musicVol = try { am.getStreamVolume(AudioManager.STREAM_MUSIC) } catch (_: Exception) { 5 }
        val musicMax = try { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } catch (_: Exception) { 15 }
        val notifVol = try { am.getStreamVolume(AudioManager.STREAM_NOTIFICATION) } catch (_: Exception) { 5 }

        val isMusicAdequate = musicVol > 0 && musicMax > 0
        val isSpeakerAdequate = notifVol > 0 || musicVol > 0

        // Check if simultaneous dual-output is supported
        val isSimultaneousSupported = checkSimultaneousDualPlaybackSupported(am, isBtA2dp, hasSpeaker)

        val primaryRoute = when {
            policy == AudioRoutingPolicy.FORCE_PHONE_SPEAKER -> AudioRouteType.BUILTIN_SPEAKER
            isBtA2dp -> AudioRouteType.BLUETOOTH_A2DP
            isBtSco -> AudioRouteType.BLUETOOTH_SCO
            isWired -> AudioRouteType.WIRED_HEADSET
            hasSpeaker -> AudioRouteType.BUILTIN_SPEAKER
            else -> AudioRouteType.UNKNOWN
        }

        return AudioRoutingStatus(
            hasBuiltInSpeaker = hasSpeaker,
            isBluetoothA2dpConnected = isBtA2dp,
            isBluetoothScoActive = isBtSco,
            isWiredHeadsetConnected = isWired,
            isSimultaneousDualOutputSupported = isSimultaneousSupported,
            activePrimaryRoute = primaryRoute,
            activePolicy = policy,
            isSpeakerVolumeAdequate = isSpeakerAdequate,
            isMusicVolumeAdequate = isMusicAdequate,
            limitationDetails = LIMITATION_DOCUMENTATION,
            lastFallbackTriggered = null
        )
    }

    /**
     * Evaluates if the underlying Android system/HAL supports concurrent dual-sink routing.
     * Standard AOSP returns false due to single active sink audio policy.
     */
    fun checkSimultaneousDualPlaybackSupported(
        audioManager: AudioManager,
        isBtA2dp: Boolean,
        hasSpeaker: Boolean
    ): Boolean {
        // Standard Android AOSP Audio Policy does NOT support simultaneous multi-sink audio playback
        // on public APIs. If Bluetooth is not connected, the question is not applicable (false).
        if (!isBtA2dp || !hasSpeaker) {
            return false
        }

        // Vendor multi-sink detection (e.g. Samsung Dual Audio / custom platform properties)
        return try {
            val vendorDualAudio = System.getProperty("ro.audio.dual_audio", "false")
            vendorDualAudio.equals("true", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Determines whether fallback to phone speaker should be triggered for a given announcement.
     */
    fun shouldTriggerSpeakerFallback(
        status: AudioRoutingStatus,
        isCriticalSafetyAlert: Boolean,
        ttsDispatchSuccess: Boolean
    ): Boolean {
        // If user explicitly forced phone speaker, no fallback is needed (already on speaker)
        if (status.activePolicy == AudioRoutingPolicy.FORCE_PHONE_SPEAKER) {
            return false
        }

        // 1. If TTS failed on Bluetooth
        if (!ttsDispatchSuccess && status.isBluetoothA2dpConnected) {
            return true
        }

        // 2. If Bluetooth is connected but volume is completely muted (0)
        if (status.isBluetoothA2dpConnected && !status.isMusicVolumeAdequate) {
            return true
        }

        // 3. For critical safety alerts in DUAL_ATTEMPT mode
        if (isCriticalSafetyAlert && status.activePolicy == AudioRoutingPolicy.DUAL_ATTEMPT_SEQUENTIAL) {
            return true
        }

        return false
    }
}
