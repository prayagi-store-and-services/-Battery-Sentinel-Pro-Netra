package com.example.service

/** Net stored-energy rate, NOT charger output or direct charging-input power. */
internal enum class EnergySpeed { SLOW, NORMAL, FAST, ULTRA_FAST, HYPER_FAST, EXTREME_FAST }
internal data class EnergySpeedReading(val watts: Double? = null, val speed: EnergySpeed? = null)

/** Event-driven, bounded observed-time window. No timers, polling or nominal battery assumptions. */
internal class EnergyCounterSpeedPolicy {
  private data class Point(val time: Long, val energy: Long)
  private val points = ArrayDeque<Point>()
  private var candidate: EnergySpeed? = null
  private var candidateSince: Long? = null
  private var lastReading = EnergySpeedReading()
  val sampleCount: Int get() = points.size

  fun reset() {
    points.clear(); candidate = null; candidateSince = null; lastReading = EnergySpeedReading()
  }

  fun observe(charging: Boolean, energyNwh: Long?, elapsedMillis: Long?): EnergySpeedReading {
    if (!charging || energyNwh == null || energyNwh <= 0 || elapsedMillis == null || elapsedMillis < 0) {
      reset(); return lastReading
    }
    val previous = points.lastOrNull()
    if (previous != null && elapsedMillis == previous.time && energyNwh == previous.energy) return lastReading
    if (previous != null && (elapsedMillis <= previous.time || elapsedMillis - previous.time > WINDOW_MILLIS || energyNwh <= previous.energy)) {
      reset()
    }
    points.addLast(Point(elapsedMillis, energyNwh))
    while (points.size > MAX_POINTS) points.removeFirst()
    val lower = elapsedMillis - WINDOW_MILLIS
    while (points.size > 2 && points.elementAt(1).time <= lower) points.removeFirst()
    if (points.size < 2 || points.first().time > lower) {
      candidate = null; candidateSince = null; lastReading = EnergySpeedReading(); return lastReading
    }
    // Interpolate only the oldest partial interval of the observed 10-second window.
    val first = points.first(); val second = points.elementAt(1)
    // Subtract integer counters before division. Decimal rational arithmetic avoids
    // losing a boundary (for example exact 5 W) to cancellation of large counters.
    val interval = java.math.BigDecimal.valueOf(second.time - first.time)
    val delta = java.math.BigDecimal.valueOf(energyNwh).subtract(java.math.BigDecimal.valueOf(first.energy))
    val partial = java.math.BigDecimal.valueOf(second.energy).subtract(java.math.BigDecimal.valueOf(first.energy))
      .multiply(java.math.BigDecimal.valueOf(lower - first.time))
    val watts = delta.multiply(interval).subtract(partial)
      .multiply(java.math.BigDecimal("0.0036"))
      .divide(interval.multiply(java.math.BigDecimal.valueOf(WINDOW_MILLIS)), java.math.MathContext.DECIMAL128)
      .toDouble()
    val observed = classify(watts)
    if (observed == null) {
      reset(); return lastReading
    }
    if (candidate != observed) { candidate = observed; candidateSince = elapsedMillis }
    val stable = candidateSince?.let { elapsedMillis - it >= STABLE_MILLIS } == true
    lastReading = EnergySpeedReading(watts, observed.takeIf { stable })
    return lastReading
  }

  companion object {
    const val WINDOW_MILLIS = 10_000L
    const val STABLE_MILLIS = 10_000L
    const val MAX_POINTS = 64
    fun classify(watts: Double): EnergySpeed? = when {
      !watts.isFinite() || watts <= 0.0 -> null
      watts < 5.0 -> EnergySpeed.SLOW
      watts < 10.0 -> EnergySpeed.NORMAL
      watts < 20.0 -> EnergySpeed.FAST
      watts < 50.0 -> EnergySpeed.ULTRA_FAST
      watts < 100.0 -> EnergySpeed.HYPER_FAST
      else -> EnergySpeed.EXTREME_FAST
    }
  }
}
