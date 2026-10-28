package com.example.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thermal Guardian session metrics, kept only on the phone.
 *
 * Each session records exactly what Deepak asked to measure: start temperature and time,
 * charging state at start, every control the Guardian applied, before/after temperature,
 * cooling rate, whether the phone came back to the safe band, warnings, and
 * sensor-unavailable events. Test runs are flagged so they never inflate real numbers.
 * Nothing here leaves the phone; there is no account and no identifier.
 */
data class ThermalSession(
    val startMs: Long,
    val startTempC: Float,
    val chargingAtStart: Boolean?,
    val maxTempC: Float,
    val events: List<String>,
    val endMs: Long?,
    val endTempC: Float?,
    val coolingRateCPerMin: Float?,
    val reachedSafeBand: Boolean,
    val isTest: Boolean
)

object ThermalGuardianLog {

    private const val PREFS = "netra_thermal_guardian"
    private const val KEY_EVENTS = "pending_events"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_START_MS = "session_start_ms"
    private const val KEY_START_TEMP = "session_start_temp"
    private const val KEY_START_CHARGING = "session_start_charging"
    private const val KEY_MAX_TEMP = "session_max_temp"
    private const val MAX_SESSIONS = 30
    private const val MAX_EVENTS = 200
    private const val MAX_EVENT_CHARS = 500

    private var prefs: SharedPreferences? = null

    private val _sessions = MutableStateFlow<List<ThermalSession>>(emptyList())
    val sessions: StateFlow<List<ThermalSession>> = _sessions.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _sessions.value = loadAll()
    }

    /** A control episode begins (first light/active/severe escalation). */
    @Synchronized
    fun beginSession(startMs: Long, startTempC: Float, charging: Boolean?) {
        val p = prefs ?: return
        p.edit()
            .putLong(KEY_START_MS, startMs)
            .putFloat(KEY_START_TEMP, startTempC)
            .putInt(KEY_START_CHARGING, if (charging == null) -1 else if (charging) 1 else 0)
            .putFloat(KEY_MAX_TEMP, startTempC)
            .putString(KEY_EVENTS, "[]")
            .apply()
    }

    /** Track the hottest reading seen during the open episode. */
    @Synchronized
    fun trackMax(tempC: Float) {
        val p = prefs ?: return
        if (!p.contains(KEY_START_MS)) return
        if (tempC > p.getFloat(KEY_MAX_TEMP, 0f)) p.edit().putFloat(KEY_MAX_TEMP, tempC).apply()
    }

    @Synchronized
    fun addEvent(event: String) {
        val p = prefs ?: return
        val arr = try { JSONArray(p.getString(KEY_EVENTS, "[]")) } catch (_: Exception) { JSONArray() }
        val bounded = JSONArray()
        for (i in maxOf(0, arr.length() - MAX_EVENTS + 1) until arr.length()) {
            bounded.put(arr.optString(i).take(MAX_EVENT_CHARS))
        }
        bounded.put("${System.currentTimeMillis()}: $event".take(MAX_EVENT_CHARS))
        p.edit().putString(KEY_EVENTS, bounded.toString()).apply()
    }

    /** Close the open episode. [reachedSafeBand] true when the phone came back under 37 C. */
    @Synchronized
    fun endSession(endMs: Long, endTempC: Float?, reachedSafeBand: Boolean, isTest: Boolean = false) {
        val p = prefs ?: return
        if (!p.contains(KEY_START_MS)) return
        val startMs = p.getLong(KEY_START_MS, 0L)
        val startTemp = p.getFloat(KEY_START_TEMP, 0f)
        val chargingInt = p.getInt(KEY_START_CHARGING, -1)
        val events = try {
            val arr = JSONArray(p.getString(KEY_EVENTS, "[]"))
            (maxOf(0, arr.length() - MAX_EVENTS) until arr.length()).map { arr.getString(it).take(MAX_EVENT_CHARS) }
        } catch (_: Exception) { emptyList() }
        val minutes = (endMs - startMs) / 60_000f
        val cooling = if (endTempC != null && minutes > 0.25f) (startTemp - endTempC) / minutes else null
        val session = ThermalSession(
            startMs = startMs,
            startTempC = startTemp,
            chargingAtStart = if (chargingInt < 0) null else chargingInt == 1,
            maxTempC = p.getFloat(KEY_MAX_TEMP, startTemp),
            events = events,
            endMs = endMs,
            endTempC = endTempC,
            coolingRateCPerMin = cooling,
            reachedSafeBand = reachedSafeBand,
            isTest = isTest
        )
        val all = (loadAll() + session).takeLast(MAX_SESSIONS)
        p.edit()
            .remove(KEY_START_MS).remove(KEY_START_TEMP).remove(KEY_START_CHARGING)
            .remove(KEY_MAX_TEMP).remove(KEY_EVENTS)
            .putString(KEY_SESSIONS, JSONArray(all.map { it.toJson() }).toString())
            .apply()
        _sessions.value = all
    }

    /** A test run is stored as a one-shot closed session, flagged, never mixed with real ones. */
    @Synchronized
    fun recordTest(events: List<String>, tempC: Float?, charging: Boolean?) {
        val p = prefs ?: return
        val now = System.currentTimeMillis()
        val session = ThermalSession(
            startMs = now, startTempC = tempC ?: 0f, chargingAtStart = charging,
            maxTempC = tempC ?: 0f, events = events.takeLast(MAX_EVENTS).map { it.take(MAX_EVENT_CHARS) }, endMs = now, endTempC = tempC,
            coolingRateCPerMin = null, reachedSafeBand = tempC != null && tempC < ThermalGuardianPolicy.RECOVER_C,
            isTest = true
        )
        val all = (loadAll() + session).takeLast(MAX_SESSIONS)
        p.edit().putString(KEY_SESSIONS, JSONArray(all.map { it.toJson() }).toString()).apply()
        _sessions.value = all
    }

    private fun loadAll(): List<ThermalSession> {
        val p = prefs ?: return emptyList()
        return try {
            val arr = JSONArray(p.getString(KEY_SESSIONS, "[]"))
            (maxOf(0, arr.length() - MAX_SESSIONS) until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    private fun ThermalSession.toJson(): JSONObject = JSONObject().apply {
        put("startMs", startMs); put("startTempC", startTempC.toDouble())
        put("charging", chargingAtStart?.let { it } ?: JSONObject.NULL)
        put("maxTempC", maxTempC.toDouble())
        put("events", JSONArray(events))
        put("endMs", endMs ?: JSONObject.NULL); put("endTempC", endTempC?.toDouble() ?: JSONObject.NULL)
        put("cooling", coolingRateCPerMin?.toDouble() ?: JSONObject.NULL)
        put("safe", reachedSafeBand); put("test", isTest)
    }

    private fun fromJson(o: JSONObject): ThermalSession {
        val arr = o.optJSONArray("events") ?: JSONArray()
        return ThermalSession(
            startMs = o.optLong("startMs"),
            startTempC = o.optDouble("startTempC").toFloat(),
            chargingAtStart = if (o.isNull("charging")) null else o.optBoolean("charging"),
            maxTempC = o.optDouble("maxTempC").toFloat(),
            events = (maxOf(0, arr.length() - MAX_EVENTS) until arr.length()).map { arr.optString(it).take(MAX_EVENT_CHARS) },
            endMs = if (o.isNull("endMs")) null else o.optLong("endMs"),
            endTempC = if (o.isNull("endTempC")) null else o.optDouble("endTempC").toFloat(),
            coolingRateCPerMin = if (o.isNull("cooling")) null else o.optDouble("cooling").toFloat(),
            reachedSafeBand = o.optBoolean("safe"),
            isTest = o.optBoolean("test")
        )
    }
}
