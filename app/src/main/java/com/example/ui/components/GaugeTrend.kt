package com.example.ui.components

/** Trend states for the opt-in gauge animations. FLAT also covers unknown readings. */
internal enum class GaugeTrend { RISING, FALLING, FLAT }

/**
 * Small-hysteresis trend over real readings. FLAT switches to RISING or FALLING only when the
 * reading crosses [deadband] from the reference; an active trend holds through smaller wobble and
 * ends only when the reading reverses past [deadband] from the trend's extreme. Null or
 * non-finite readings reset to FLAT, so flat or unknown data animates nothing.
 */
internal class TrendTracker(private val deadband: Float) {
    private var reference: Float? = null
    private var trend: GaugeTrend = GaugeTrend.FLAT

    @Synchronized
    fun update(value: Float?): GaugeTrend {
        if (value == null || !value.isFinite()) {
            reference = null
            trend = GaugeTrend.FLAT
            return trend
        }
        val ref = reference
        if (ref == null) {
            reference = value
            trend = GaugeTrend.FLAT
            return trend
        }
        val delta = value - ref
        when (trend) {
            GaugeTrend.FLAT -> when {
                delta >= deadband -> { trend = GaugeTrend.RISING; reference = value }
                delta <= -deadband -> { trend = GaugeTrend.FALLING; reference = value }
            }
            GaugeTrend.RISING -> {
                if (value > ref) reference = value
                else if (value <= ref - deadband) { trend = GaugeTrend.FLAT; reference = value }
            }
            GaugeTrend.FALLING -> {
                if (value < ref) reference = value
                else if (value >= ref + deadband) { trend = GaugeTrend.FLAT; reference = value }
            }
        }
        return trend
    }

    fun current(): GaugeTrend = trend
}
