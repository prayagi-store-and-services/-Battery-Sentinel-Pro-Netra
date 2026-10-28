package com.example

import com.example.service.FullChargeObservation
import org.junit.Assert.*
import org.junit.Test

class FullChargeObservationTest {
    private val base = 1_000_000L
    private fun FullChargeObservation.read(p: Int?, t: Long, plugged: Boolean? = true, charging: Boolean? = true) =
        sample(p, plugged, charging, base + t, t)

    @Test fun firstFullThenDisconnectReturnsObservedWindowOnce() {
        val p = FullChargeObservation()
        assertNull(p.read(90, 0))
        assertNull(p.read(100, 60_000))
        assertNull(p.read(100, 90_000))
        val s = p.read(99, 120_000, false, false)!!
        assertEquals(90, s.startPercent)
        assertEquals(base + 60_000, s.firstFullWallMs)
        assertEquals(60_000L, s.observedToFullMs)
        assertEquals(60_000L, s.connectedAfterFullMs)
        assertNull(p.read(99, 121_000, false, false))
    }
    @Test fun startingAtFullCannotInventDuration() {
        val p = FullChargeObservation()
        p.read(100, 0)
        assertNull(p.read(100, 100, false, false))
    }
    @Test fun noFullMeansNoCompletionSummary() {
        val p = FullChargeObservation()
        p.read(50, 0)
        assertNull(p.read(80, 100, false, false))
    }
    @Test fun unknownOrInvalidEvidenceClearsWindow() {
        for (invalid in listOf<Int?>(null, -1, 101)) {
            val p = FullChargeObservation()
            p.read(90, 0); p.read(invalid, 10); p.read(100, 20)
            assertNull(p.read(100, 30, false, false))
        }
        for ((c, a) in listOf(null to true, true to null)) {
            val p = FullChargeObservation()
            p.read(90, 0); p.read(100, 10, c, a)
            assertNull(p.read(100, 20, false, false))
        }
    }
    @Test fun longGapAndRollbackCannotStitchSessions() {
        for (time in listOf(-1L, FullChargeObservation.MAX_GAP_MS + 1)) {
            val p = FullChargeObservation()
            p.read(90, 0); p.read(100, time)
            assertNull(p.read(100, time + 1, false, false))
        }
    }
    @Test fun clockJumpInvalidatesEvidence() {
        val p = FullChargeObservation()
        p.read(90, 0)
        p.sample(100, true, true, base + 120_000, 100)
        assertNull(p.read(100, 200, false, false))
    }
    @Test fun restartDoesNotClaimUnknownContinuity() {
        FullChargeObservation().read(90, 0)
        val restarted = FullChargeObservation()
        restarted.read(100, 100)
        assertNull(restarted.read(100, 200, false, false))
    }
    @Test fun replugHasIndependentFirstFullAndPausedChargingIsConnectedTime() {
        val p = FullChargeObservation()
        p.read(50, 0); p.read(100, 10)
        assertNotNull(p.read(100, 20, false, false))
        p.read(80, 30); p.read(80, 40, true, false); p.read(100, 50)
        val s = p.read(100, 70, false, false)!!
        assertEquals(80, s.startPercent)
        assertEquals(20L, s.observedToFullMs)
        assertEquals(20L, s.connectedAfterFullMs)
    }
}
