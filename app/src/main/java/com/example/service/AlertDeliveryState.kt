package com.example.service

import android.content.Context
import com.example.model.FieldStatus
import com.example.model.NetraCentralState

/** Private delivery state for existing alerts, not a new notification pipeline.
 * Unknown sessions, corrupt storage and invalid clocks fail open: they cannot suppress a warning.
 */
internal class AlertDeliveryState(context: Context) {
    private val preferences = try {
        context.applicationContext.getSharedPreferences("netra_alert_delivery_v1", Context.MODE_PRIVATE)
    } catch (_: RuntimeException) { null }

    data class Target(val session: Long, val target: Int, val firedAt: Long, val muted: Boolean, val override: Int?)

    @Synchronized
    fun loadTarget(session: Long?, now: Long): Target? {
        val p = preferences ?: return null
        return try {
            if (session == null || session <= 0L || session > now || p.getLong("session", 0L) != session) null
            else {
                val firedAt = p.getLong("target_at", 0L)
                val target = p.getInt("target", -1)
                if (firedAt < session || firedAt > now || target !in 1..100) null
                else Target(session, target, firedAt, p.getBoolean("muted", false),
                    p.getInt("override", -1).takeIf { it in 1..100 })
            }
        } catch (_: RuntimeException) { null }
    }

    @Synchronized
    fun saveTarget(session: Long?, target: Int, at: Long, muted: Boolean, override: Int?): Boolean {
        val p = preferences ?: return false
        return try {
            if (session == null || session <= 0L || session > at || target !in 1..100) false
            else p.edit().putLong("session", session).putInt("target", target).putLong("target_at", at)
                .putBoolean("muted", muted).putInt("override", override ?: -1).commit()
        } catch (_: RuntimeException) { false }
    }

    /** An action can update only the delivery record for its own charging session. */
    @Synchronized
    fun action(session: Long, mute: Boolean = false, override: Int? = null, now: Long = System.currentTimeMillis()) {
        val saved = loadTarget(session, now) ?: return
        saveTarget(saved.session, saved.target, saved.firedAt, mute || saved.muted, override ?: saved.override)
    }

    private val liveConditions = mutableMapOf<String, Long>()
    private val conditionKeys = setOf("LOW_BATTERY_PROTECTION", "THERMAL_PROTECTION", "THERMAL_WARNING", "THERMAL_CRITICAL")

    /** At most one accepted speech attempt per observed condition, not a claim that TTS was heard. */
    @Synchronized
    fun claimCondition(category: String, now: Long): Boolean {
        if (category !in conditionKeys || now <= 0L) return true
        val key = "condition_$category"
        val persisted = try { preferences?.getLong(key, 0L) ?: 0L } catch (_: RuntimeException) { 0L }
        val previous = maxOf(liveConditions[category]?.takeIf { it <= now } ?: 0L,
            persisted.takeIf { it <= now } ?: 0L)
        if (previous > 0L) return false
        liveConditions[category] = now
        try { preferences?.edit()?.putLong(key, now)?.commit() } catch (_: RuntimeException) {}
        return true
    }

    fun observeRecovery(state: NetraCentralState) {
        val fields = state.fieldStates
        val level = state.batteryLevel.takeIf {
            fields.levelStatus == FieldStatus.LIVE && fields.levelObservedAt > 0L && fields.levelObservedAt == state.lastUpdateTimestamp
        }
        val temperature = state.temperatureCelsius.takeIf {
            fields.tempStatus == FieldStatus.LIVE && fields.tempObservedAt > 0L && fields.tempObservedAt == state.lastUpdateTimestamp
        }
        observeRecovery(level, temperature, state.isDataFresh)
    }

    /** Re-arm only on fresh measured recovery, never from missing or stale display values. */
    @Synchronized
    fun observeRecovery(level: Int?, temperature: Float?, fresh: Boolean) {
        if (!fresh) return
        val recovered = mutableListOf<String>()
        if (level != null && level in 35..100) recovered += "LOW_BATTERY_PROTECTION"
        if (temperature != null && temperature.isFinite() && temperature >= 0f) {
            if (temperature < 40f) recovered += listOf("THERMAL_WARNING", "THERMAL_CRITICAL")
            if (temperature <= 35f) recovered += "THERMAL_PROTECTION"
        }
        for (category in recovered) {
            liveConditions.remove(category)
            val key = "condition_$category"
            try { if (preferences?.contains(key) == true) preferences.edit().remove(key).commit() } catch (_: RuntimeException) {}
        }
    }

    @Synchronized
    fun clearTarget() {
        val p = preferences ?: return
        try {
            if (!p.contains("session") && !p.contains("target_at") && !p.contains("muted") && !p.contains("override")) return
            p.edit().remove("session").remove("target").remove("target_at").remove("muted").remove("override").commit()
        } catch (_: RuntimeException) {}
    }

    @Synchronized
    fun cooldownAt(key: String, now: Long): Long = try {
        (preferences?.getLong(key, 0L) ?: 0L).takeIf { it > 0L && it <= now } ?: 0L
    } catch (_: RuntimeException) { 0L }

    @Synchronized
    fun thresholdAt(threshold: Float, now: Long): Long = try {
        if (preferences?.getFloat("threshold_value", Float.NaN) == threshold) cooldownAt("threshold_at", now) else 0L
    } catch (_: RuntimeException) { 0L }

    @Synchronized
    fun recordThreshold(threshold: Float, now: Long) {
        val p = preferences ?: return
        try {
            if (now > 0L) p.edit().putFloat("threshold_value", threshold).putLong("threshold_at", now).commit()
        } catch (_: RuntimeException) {}
    }

    @Synchronized
    fun recordCooldown(key: String, now: Long) {
        val p = preferences ?: return
        try { if (now > 0L) p.edit().putLong(key, now).commit() } catch (_: RuntimeException) {}
    }
}
