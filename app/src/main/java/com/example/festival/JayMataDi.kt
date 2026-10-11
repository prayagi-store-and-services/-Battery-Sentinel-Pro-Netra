package com.example.festival

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

/**
 * Navratri, 11 Oct 2026 00:00 to the end of 20 Oct 2026 (phone clock, nothing sent anywhere).
 * 1. A red, orange and gold strip with "Jay Mata Di | Shubh Navratri" at the top of the screen.
 * 2. A one-time "Jay Mata Di" greeting (toast plus the phone's own text-to-speech) on the first open when the phone is audible:
 *    ringer normal, media volume above zero, no media playing, and the app's announcements not muted.
 *    A silent open does not use up the greeting.
 */
object JayMataDi {
    val START_MILLIS: Long = GregorianCalendar(2026, Calendar.OCTOBER, 11, 0, 0, 0).timeInMillis
    val END_MILLIS: Long = GregorianCalendar(2026, Calendar.OCTOBER, 21, 0, 0, 0).timeInMillis
    private const val PREFS = "netra_festival"
    private const val KEY = "jay_mata_di_2026"

    fun inWindow(nowMillis: Long): Boolean = nowMillis >= START_MILLIS && nowMillis < END_MILLIS
    fun inWindow(): Boolean = inWindow(System.currentTimeMillis())

    /** Pure rule: greet once, only inside the window. */
    fun due(nowMillis: Long, alreadyGreeted: Boolean): Boolean = !alreadyGreeted && inWindow(nowMillis)

    /** Pure rule: speak only when the phone is audible and nothing else is playing. */
    fun audible(ringerNormal: Boolean, mediaVolume: Int, mediaPlaying: Boolean): Boolean =
        ringerNormal && mediaVolume > 0 && !mediaPlaying

    fun greetIfDue(activity: Activity) {
        try {
            if (!inWindow()) return
            activity.window.decorView.post { try { installStrip(activity) } catch (_: Throwable) { } }
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!due(System.currentTimeMillis(), prefs.getBoolean(KEY, false))) return
            if (com.example.data.repository.SettingsRepository(activity.applicationContext).settings.value.announcementsMasterEnabled.not()) return
            val am = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (!audible(am.ringerMode == AudioManager.RINGER_MODE_NORMAL, am.getStreamVolume(AudioManager.STREAM_MUSIC), am.isMusicActive)) return
            prefs.edit().putBoolean(KEY, true).apply()
            Toast.makeText(activity, "Jay Mata Di", Toast.LENGTH_LONG).show()
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

    private fun installStrip(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<View>("navratri_strip") != null) return
        val kids = ArrayList<View>()
        for (i in 0 until content.childCount) kids.add(content.getChildAt(i))
        content.removeAllViews()
        val dp = activity.resources.displayMetrics.density
        val strip = TextView(activity).apply {
            tag = "navratri_strip"
            text = "Jay Mata Di  |  Shubh Navratri"
            setTextColor(Color.WHITE)
            setShadowLayer(2f, 0f, 1f, Color.argb(120, 0, 0, 0))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFFB71C1C.toInt(), 0xFFE65100.toInt(), 0xFFF2C14E.toInt(), 0xFFE65100.toInt(), 0xFFB71C1C.toInt())
            )
            @Suppress("DEPRECATION")
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(0, insets.systemWindowInsetTop + (8 * dp).toInt(), 0, (8 * dp).toInt())
                insets
            }
        }
        val body = FrameLayout(activity)
        for (v in kids) body.addView(v)
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        strip.requestApplyInsets()
    }
}
