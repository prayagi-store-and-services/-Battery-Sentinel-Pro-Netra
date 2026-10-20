package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Holds transient audio focus while a spoken announcement plays so other media pauses,
 * and releases it the moment the utterance ends or errors so paused media resumes.
 * AnnouncementEngine wraps this with a watchdog so focus can never leak if the TTS
 * callbacks never arrive.
 */
internal class TtsAudioFocusController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    var isHeld: Boolean = false
        private set

    /** Requests transient focus. Idempotent while held; a refusal never blocks the announcement itself. */
    @Synchronized
    fun acquire(): Boolean {
        if (isHeld) return true
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            val result = audioManager.requestAudioFocus(request)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = request
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        isHeld = granted
        return granted
    }

    /** Releases focus so paused media resumes. Safe to call repeatedly. */
    @Synchronized
    fun release(): Boolean {
        if (!isHeld) return false
        isHeld = false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: return false
            focusRequest = null
            audioManager.abandonAudioFocusRequest(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
}
