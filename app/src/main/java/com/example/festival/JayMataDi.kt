package com.example.festival

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

/**
 * Navratri greeting. From 11 Oct 2026 00:00 (phone clock) until the end of 20 Oct 2026 the app says "Jay Mata Di"
 * once, on the first open after that time: a short line on screen plus a spoken greeting by the phone's own
 * text-to-speech. Nothing is sent anywhere. Nothing is spoken when the phone is silent or the app's announcements are muted.
 */
object JayMataDi {
    val START_MILLIS: Long = GregorianCalendar(2026, Calendar.OCTOBER, 11, 0, 0, 0).timeInMillis
    val END_MILLIS: Long = GregorianCalendar(2026, Calendar.OCTOBER, 21, 0, 0, 0).timeInMillis
    private const val PREFS = "netra_festival"
    private const val KEY = "jay_mata_di_2026"

    /** Pure rule: greet once, only inside the window. */
    fun due(nowMillis: Long, alreadyGreeted: Boolean): Boolean =
        !alreadyGreeted && nowMillis >= START_MILLIS && nowMillis < END_MILLIS

    fun greetIfDue(activity: Activity) {
        try {
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!due(System.currentTimeMillis(), prefs.getBoolean(KEY, false))) return
            prefs.edit().putBoolean(KEY, true).apply()
            Toast.makeText(activity, "Jay Mata Di", Toast.LENGTH_LONG).show()
            if (com.example.data.repository.SettingsRepository(activity.applicationContext).settings.value.announcementsMasterEnabled.not()) return
            val am = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am == null || am.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
                am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return
            val appCtx = activity.applicationContext
            var tts: TextToSpeech? = null
            tts = TextToSpeech(appCtx) { status ->
                val engine = tts ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) { engine.shutdown(); return@TextToSpeech }
                val hindi = Locale("hi", "IN")
                val text: String
                if (engine.isLanguageAvailable(hindi) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = hindi
                    text = "\u091C\u092F \u092E\u093E\u0924\u093E \u0926\u0940"
                } else {
                    engine.language = Locale.US
                    text = "Jay Mata Di"
                }
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jay_mata_di")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ engine.shutdown() }, 6000)
            }
        } catch (_: Throwable) {
        }
    }
}
