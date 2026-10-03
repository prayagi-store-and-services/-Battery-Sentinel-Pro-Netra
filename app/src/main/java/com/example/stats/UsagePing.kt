package com.example.stats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Anonymous active-user count. At most once per UTC day (and once per UTC month) the app adds +1 to a public counter
 * document in Firestore (netra_active/<appId>_<yyyyMMdd> and <appId>_<yyyyMM>). The request carries NO device id, install id,
 * account, location, IP-derived data we keep, or any app data: just the document name and "increment by 1".
 * The user can turn it off in Settings (default is on).
 */
object UsagePing {
    const val APP_ID = "battery-sentinel"
    private const val PREFS = "netra_usage_count"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DAY = "last_day"
    private const val KEY_MONTH = "last_month"
    private const val COMMIT_URL = "https://firestore.googleapis.com/v1/projects/netra-ai-jan/databases/(default)/documents:commit"
    private const val DOC_ROOT = "projects/netra-ai-jan/databases/(default)/documents/netra_active/"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun dayId(now: Date = Date()): String = fmt("yyyyMMdd", now)
    fun monthId(now: Date = Date()): String = fmt("yyyyMM", now)

    private fun fmt(pattern: String, now: Date): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)

    /** The whole request body: increment the "count" field of one document by 1. Nothing else is sent. */
    fun commitBody(docId: String): String = JSONObject().put(
        "writes",
        JSONArray().put(
            JSONObject().put(
                "transform",
                JSONObject()
                    .put("document", DOC_ROOT + docId)
                    .put(
                        "fieldTransforms",
                        JSONArray().put(
                            JSONObject().put("fieldPath", "count")
                                .put("increment", JSONObject().put("integerValue", "1"))
                        )
                    )
            )
        )
    ).toString()

    /** Call from a background thread. Sends the daily and monthly +1 at most once each; a failed send is retried next open. */
    fun pingIfDue(context: Context) {
        if (!isEnabled(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = Date()
        val day = dayId(now)
        val month = monthId(now)
        if (prefs.getString(KEY_DAY, "") != day && send(APP_ID + "_" + day)) prefs.edit().putString(KEY_DAY, day).apply()
        if (prefs.getString(KEY_MONTH, "") != month && send(APP_ID + "_" + month)) prefs.edit().putString(KEY_MONTH, month).apply()
    }

    private fun send(docId: String): Boolean {
        return try {
            val c = URL(COMMIT_URL).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(commitBody(docId).toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode == 200
            c.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }
}
