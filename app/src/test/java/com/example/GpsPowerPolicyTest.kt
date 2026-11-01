package com.example

import com.example.service.GpsPowerPolicy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsPowerPolicyTest {

    private val policy = GpsPowerPolicy()

    @Test
    fun `moving keeps location on`() {
        var s = policy.initialState(0)
        val (s2, on) = policy.tick(s, 10_000, movingNow = true)
        assertTrue(on)
        s = s2
        val (_, on2) = policy.tick(s, 20_000, movingNow = true)
        assertTrue(on2)
    }

    @Test
    fun `grace period keeps location on briefly after stopping`() {
        var s = policy.initialState(0)
        s = policy.tick(s, 10_000, movingNow = true).first
        val (_, onDuringGrace) = policy.tick(s, 10_000 + 30_000, movingNow = false)
        assertTrue(onDuringGrace)
        val (_, onAfterGrace) = policy.tick(s, 10_000 + 90_000, movingNow = false)
        assertFalse(onAfterGrace)
    }

    @Test
    fun `still phone gets a short pulse every five minutes`() {
        var s = policy.initialState(0)
        s = policy.tick(s, 0, movingNow = true).first
        // Stop moving at t=0; grace ends at 60s.
        val base = 60_000L
        var (state, on) = policy.tick(s, base + 60_000, movingNow = false) // t=120s, still: no pulse yet (5 min not elapsed)
        assertFalse(on)
        // At grace + 5 min the pulse starts.
        val pulseAt = base + 5 * 60_000L // t=360s
        val (s2, pulseOn) = policy.tick(state, pulseAt, movingNow = false)
        assertTrue(pulseOn)
        // During the 5s pulse window it stays on.
        val (s3, stillInPulse) = policy.tick(s2, pulseAt + 3_000, movingNow = false)
        assertTrue(stillInPulse)
        // After the pulse window it turns off again.
        val (_, afterPulse) = policy.tick(s3, pulseAt + 6_000, movingNow = false)
        assertFalse(afterPulse)
    }

    @Test
    fun `movement during a pulse keeps location on and resets the timer`() {
        var s = policy.initialState(0)
        s = policy.tick(s, 0, movingNow = true).first
        val pulseAt = 60_000L + 5 * 60_000L
        s = policy.tick(s, pulseAt, movingNow = false).first
        val (s2, on) = policy.tick(s, pulseAt + 2_000, movingNow = true)
        assertTrue(on)
        // Grace restarts from the new movement.
        val (_, offLater) = policy.tick(s2, pulseAt + 2_000 + 61_000, movingNow = false)
        assertFalse(offLater)
    }
}
