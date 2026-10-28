package com.example.service

/** Existing raw battery events only. No sampler or durable in-progress session is created.
 * Missing evidence resets the window. A restarted process starts a new observation window.
 */
class FullChargeObservation {
    data class Summary(
        val firstFullWallMs: Long,
        val startPercent: Int,
        val observedToFullMs: Long,
        val connectedAfterFullMs: Long
    )
    private data class Window(val startPercent: Int, val startElapsed: Long,
        val lastElapsed: Long, val lastWall: Long, val fullElapsed: Long? = null,
        val fullWall: Long? = null)
    private var window: Window? = null

    /** A result is returned once, at an observed disconnect, never merely at 100%.
     * 15min gaps and wall-clock changes >60s compared with monotonic time invalidate evidence.
     * Null/invalid/raw unknown inputs cannot reuse a retained dashboard percentage.
     */
    fun sample(percent: Int?, connected: Boolean?, charging: Boolean?, wallMs: Long, elapsedMs: Long): Summary? {
        var old = window
        val elapsedDelta = old?.let { elapsedMs - it.lastElapsed }
        val wallDelta = old?.let { wallMs - it.lastWall }
        if (elapsedMs < 0 || wallMs <= 0 || (old != null &&
                (elapsedDelta!! < 0 || elapsedDelta > MAX_GAP_MS || wallDelta!! < 0 ||
                    kotlin.math.abs(wallDelta - elapsedDelta) > CLOCK_TOLERANCE_MS))) {
            window = null
            return null
        }
        if (connected == false) {
            window = null
            return old?.let { w ->
                val full = w.fullElapsed ?: return@let null
                Summary(w.fullWall!!, w.startPercent, full - w.startElapsed, elapsedMs - full)
            }
        }
        if (connected != true || percent == null || percent !in 0..100 || charging == null) {
            window = null
            return null
        }
        if (old == null) {
            // Starting at full says nothing about the time required to get there.
            if (percent < 100 && charging) window = Window(percent, elapsedMs, elapsedMs, wallMs)
            return null
        }
        // Paused charging can still be connected (OEM optimization). Keep the observed
        // wall-clock connected interval, but never infer a physically charging duration.
        val firstFull = old.fullElapsed == null && percent == 100
        window = old.copy(lastElapsed = elapsedMs, lastWall = wallMs,
            fullElapsed = if (firstFull) elapsedMs else old.fullElapsed,
            fullWall = if (firstFull) wallMs else old.fullWall)
        return null
    }

    companion object {
        const val MAX_GAP_MS = 15L * 60_000L
        const val CLOCK_TOLERANCE_MS = 60_000L
    }
}
