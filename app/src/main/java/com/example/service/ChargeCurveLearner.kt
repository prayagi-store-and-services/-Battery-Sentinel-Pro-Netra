package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.annotation.VisibleForTesting
import java.util.Arrays
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject

/**
 * Learns the phone's real seconds-per-percent charge and discharge curve from observed
 * battery level transitions. Values are kept per battery level (0-100), separately for
 * charging and discharging, and split into three temperature bands (below 30C, 30-38C,
 * above 38C), because the same phone charges very differently when hot (taper and
 * thermal throttling are learned, never assumed). EWMA-smoothed over real observations
 * only. A level without confident data falls back to the learned band average; a band
 * with no data at all reports no estimate so the caller can fall back honestly.
 * Persistence is plain SharedPreferences JSON; no Room migration.
 */
internal object ChargeCurveLearner {

    enum class Band(val id: Int) { COOL(0), WARM(1), HOT(2) }

    /** [minutes] is the learned estimate; [confident] means at least 80% of the summed levels have their own observations. */
    data class Eta(val minutes: Int, val confident: Boolean)

    private const val PREFS_NAME = "charge_curve_learner"
    private const val ALPHA = 0.35f
    private const val MIN_OBSERVATIONS = 2
    private const val MAX_STEP_GAP_MS = 30 * 60_000L
    private const val MIN_STEP_SECONDS = 1f
    private const val MAX_STEP_SECONDS = 3600f
    private const val SAVE_THROTTLE_MS = 60_000L
    private const val MAX_CHARGING_MINUTES = 720
    private const val MAX_DISCHARGING_MINUTES = 1440
    private const val CONFIDENT_FRACTION = 0.8

    private var prefs: SharedPreferences? = null

    // [mode 0=charging 1=discharging][band][level] -> ewma seconds per percent / observation count
    private val seconds = Array(2) { Array(3) { FloatArray(101) } }
    private val counts = Array(2) { Array(3) { IntArray(101) } }
    private var lastSaveAtMs = 0L

    private var lastLevel: Int? = null
    private var lastAtMs = 0L
    private var lastCharging = false
    private var lastBand: Band? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    @VisibleForTesting
    @Synchronized
    fun resetForTesting(context: Context, prefsName: String) {
        prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        for (m in 0..1) {
            for (b in 0..2) {
                Arrays.fill(seconds[m][b], 0f)
                Arrays.fill(counts[m][b], 0)
            }
        }
        lastLevel = null
        lastAtMs = 0L
        lastCharging = false
        lastBand = null
        lastSaveAtMs = 0L
        load()
    }

    @VisibleForTesting
    @Synchronized
    fun saveNow() {
        val keep = lastSaveAtMs
        lastSaveAtMs = 0L
        maybeSave(keep + SAVE_THROTTLE_MS + 1)
    }

    private fun bandOf(tempC: Float?): Band? = when {
        tempC == null -> null
        tempC < 30f -> Band.COOL
        tempC <= 38f -> Band.WARM
        else -> Band.HOT
    }

    /** Observes one battery snapshot. Only real level transitions in a stable mode and band teach the curve. */
    @Synchronized
    fun observeTransition(level: Int?, status: Int, plugged: Int, tempC: Float?, nowMs: Long) {
        val charging = when {
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) && plugged > 0 -> true
            status == BatteryManager.BATTERY_STATUS_DISCHARGING && plugged == 0 -> false
            else -> {
                lastLevel = null
                lastBand = null
                return
            }
        }
        val band = bandOf(tempC)
        if (level == null || level !in 0..100 || band == null) {
            lastLevel = null
            lastBand = null
            return
        }
        val prev = lastLevel
        if (prev != null && lastCharging == charging && lastBand == band) {
            val dt = nowMs - lastAtMs
            val steps = if (charging) level - prev else prev - level
            if (steps > 0 && dt in 1..MAX_STEP_GAP_MS) {
                val perStepSec = dt.toFloat() / 1000f / steps
                val mode = if (charging) 0 else 1
                if (charging) {
                    for (l in (prev + 1)..level) update(mode, band.id, l, perStepSec)
                } else {
                    for (l in (prev - 1) downTo level) update(mode, band.id, l, perStepSec)
                }
                maybeSave(nowMs)
            }
        }
        lastLevel = level
        lastAtMs = nowMs
        lastCharging = charging
        lastBand = band
    }

    private fun update(mode: Int, band: Int, level: Int, sampleSec: Float) {
        if (sampleSec < MIN_STEP_SECONDS || sampleSec > MAX_STEP_SECONDS) return
        val old = counts[mode][band][level]
        seconds[mode][band][level] = if (old <= 0) {
            sampleSec
        } else {
            seconds[mode][band][level] + ALPHA * (sampleSec - seconds[mode][band][level])
        }
        counts[mode][band][level] = (old + 1).coerceAtMost(1000)
    }

    /**
     * Learned minutes from [fromLevel] to [toLevel] for the temperature band of [tempC].
     * Levels without confident data use the band average; a band with no data returns null.
     * With unknown temperature the band with the most observations answers.
     */
    @Synchronized
    fun estimateMinutes(fromLevel: Int, toLevel: Int, charging: Boolean, tempC: Float?): Eta? {
        if (fromLevel !in 0..100 || toLevel !in 0..100) return null
        if (charging && toLevel <= fromLevel) return null
        if (!charging && toLevel >= fromLevel) return null
        val band = bandOf(tempC) ?: bestBand(charging) ?: return null
        val mode = if (charging) 0 else 1
        val avg = bandAverage(mode, band.id) ?: return null
        var totalSec = 0f
        var covered = 0
        var levels = 0
        val range = if (charging) (fromLevel + 1)..toLevel else (toLevel + 1)..fromLevel
        for (l in range) {
            levels++
            totalSec += if (counts[mode][band.id][l] >= MIN_OBSERVATIONS) seconds[mode][band.id][l] else avg
            if (counts[mode][band.id][l] >= MIN_OBSERVATIONS) covered++
        }
        if (levels == 0) return null
        val minutes = ceil(totalSec / 60f).toInt()
        val cap = if (charging) MAX_CHARGING_MINUTES else MAX_DISCHARGING_MINUTES
        if (minutes !in 1..cap) return null
        val confident = covered.toDouble() / levels >= CONFIDENT_FRACTION
        return Eta(minutes, confident)
    }

    private fun bandAverage(mode: Int, band: Int): Float? {
        var weighted = 0f
        var n = 0
        for (l in 0..100) {
            val c = counts[mode][band][l]
            if (c > 0) {
                weighted += seconds[mode][band][l] * c
                n += c
            }
        }
        return if (n > 0) weighted / n else null
    }

    private fun bestBand(charging: Boolean): Band? {
        val mode = if (charging) 0 else 1
        var best: Band? = null
        var bestCount = 0
        for (band in Band.values()) {
            val n = counts[mode][band.id].sum()
            if (n > bestCount) {
                bestCount = n
                best = band
            }
        }
        return best
    }

    private fun load() {
        val p = prefs ?: return
        try {
            val root = JSONObject(p.getString("v1", "{}") ?: "{}")
            for ((modeIdx, key) in listOf(0 to "c", 1 to "d")) {
                val modes = root.optJSONObject(key) ?: continue
                for (band in Band.values()) {
                    val levels = modes.optJSONObject(band.id.toString()) ?: continue
                    val keys = levels.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val lvl = k.toIntOrNull() ?: continue
                        if (lvl !in 0..100) continue
                        val pair: JSONArray = levels.optJSONArray(k) ?: continue
                        seconds[modeIdx][band.id][lvl] = pair.optDouble(0, 0.0).toFloat()
                        counts[modeIdx][band.id][lvl] = pair.optInt(1, 0)
                    }
                }
            }
        } catch (_: Exception) {
            // Corrupt or absent stored curve means no learned data, never a crash.
        }
    }

    @Synchronized
    private fun maybeSave(nowMs: Long) {
        if (nowMs - lastSaveAtMs < SAVE_THROTTLE_MS) return
        lastSaveAtMs = nowMs
        val p = prefs ?: return
        try {
            val root = JSONObject()
            for ((modeIdx, key) in listOf(0 to "c", 1 to "d")) {
                val modes = JSONObject()
                for (band in Band.values()) {
                    val levels = JSONObject()
                    for (lvl in 0..100) {
                        if (counts[modeIdx][band.id][lvl] > 0) {
                            levels.put(
                                lvl.toString(),
                                JSONArray()
                                    .put(seconds[modeIdx][band.id][lvl].toDouble())
                                    .put(counts[modeIdx][band.id][lvl])
                            )
                        }
                    }
                    if (levels.length() > 0) modes.put(band.id.toString(), levels)
                }
                if (modes.length() > 0) root.put(key, modes)
            }
            p.edit().putString("v1", root.toString()).apply()
        } catch (_: Exception) {
            // A failed save loses at most recent samples; learning continues next launch.
        }
    }
}
