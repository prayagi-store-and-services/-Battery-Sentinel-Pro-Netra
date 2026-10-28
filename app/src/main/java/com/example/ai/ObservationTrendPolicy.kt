package com.example.ai

/** UTC-day aggregates of stored samples, not duration-weighted exposure or measured capacity. */
data class DailyBatteryObservation(
    val utcDay: Long,
    val sampleCount: Int,
    val meanTemperatureC: Double
)

data class ObservationComparison(
    val label: String,
    val windowDays: Int,
    val currentDays: Int,
    val previousDays: Int,
    val requiredDays: Int,
    val currentSamples: Int,
    val previousSamples: Int,
    val temperatureDeltaC: Double?
) {
    val coverageLabel: String get() = if (temperatureDeltaC == null) "Not enough days recorded" else "Enough days recorded (limited)"
    val summary: String get() = if (temperatureDeltaC == null) "Temperature comparison unavailable"
        else String.format(java.util.Locale.getDefault(), "Recorded daily-mean temperature change: %+.1f C", temperatureDeltaC)
    val evidence: String get() = "Completed UTC days: last $windowDays days: $currentDays, prior $windowDays days: $previousDays (need $requiredDays each); samples: current $currentSamples, prior $previousSamples"
}

object ObservationTrendPolicy {
    const val DAY_MS = 86_400_000L
    /** Compare completed days only. Dense polling on one day cannot satisfy coverage. */
    fun compare(days: List<DailyBatteryObservation>, nowMs: Long, windowDays: Int): ObservationComparison {
        require(windowDays == 7 || windowDays == 30)
        val today = Math.floorDiv(nowMs, DAY_MS)
        val required = if (windowDays == 7) 5 else 21
        val valid = days.filter { it.sampleCount >= 6 && it.meanTemperatureC.isFinite() && it.meanTemperatureC in -50.0..100.0 }
            .groupBy { it.utcDay }.map { (_, items) ->
                // Defend duplicate inputs without counting a day twice.
                val samples = items.sumOf { it.sampleCount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                DailyBatteryObservation(items.first().utcDay, samples,
                    items.sumOf { it.meanTemperatureC * it.sampleCount } / items.sumOf { it.sampleCount.toDouble() })
            }
        val current = valid.filter { it.utcDay >= today - windowDays && it.utcDay < today }
        val previous = valid.filter { it.utcDay >= today - 2 * windowDays && it.utcDay < today - windowDays }
        val enough = current.size >= required && previous.size >= required
        return ObservationComparison(if (windowDays == 7) "Weekly recorded temperature" else "Monthly recorded temperature", windowDays,
            current.size, previous.size, required,
            current.sumOf { it.sampleCount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            previous.sumOf { it.sampleCount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            if (enough) current.map { it.meanTemperatureC }.average() - previous.map { it.meanTemperatureC }.average() else null)
    }
}
