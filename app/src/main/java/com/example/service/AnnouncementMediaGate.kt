package com.example.service

import android.content.Context
import android.media.AudioManager
import android.os.Build

/** Best-effort local playback visibility only. No audio focus, media control or permissions. */
internal object AnnouncementMediaGate {
    fun shouldSkip(context: Context): Boolean = try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio == null) true else evaluate(
            musicActive = { audio.isMusicActive },
            playbackActive = { Build.VERSION.SDK_INT >= 26 && audio.activePlaybackConfigurations.isNotEmpty() }
        )
    } catch (_: Exception) { true }

    /** A failed observation is not proof that the phone is quiet. */
    internal fun evaluate(musicActive: () -> Boolean, playbackActive: () -> Boolean): Boolean = try {
        musicActive() || playbackActive()
    } catch (_: Exception) { true }
}
