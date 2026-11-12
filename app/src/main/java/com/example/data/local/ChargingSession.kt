package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Completed recorded portion, not proof of an uninterrupted charging session.
 * Existing rows may contain backdated legacy fields; do not infer full coverage.
 * avgPowerWatts is a two-endpoint estimate, not a time-weighted power measurement.
 */
@Entity(tableName = "charging_sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val peakTemperature: Float,
    val avgPowerWatts: Float,
    val chargerType: String, // "AC", "USB", "WIRELESS", "FAST"
    val durationMinutes: Int
)
