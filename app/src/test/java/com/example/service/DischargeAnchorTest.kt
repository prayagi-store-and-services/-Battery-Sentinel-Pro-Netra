package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DischargeAnchorTest {
    private class Fake(var v: Long? = null) : DischargeAnchorStore {
        override fun get() = v
        override fun set(epochMs: Long) { v = epochMs }
        override fun clear() { v = null }
    }

    @Test
    fun durationAfterRestartIsRealElapsedTime() {
        val store = Fake(1_000_000L)
        val now = 1_000_000L + 6 * 3_600_000L
        val nowElapsed = 500_000L
        val start = DischargeAnchor.elapsedStart(store, now, nowElapsed)
        assertEquals(6 * 3_600_000L, nowElapsed - start)
    }

    @Test
    fun noSavedStartBeginsNowAndSaves() {
        val store = Fake()
        val start = DischargeAnchor.elapsedStart(store, 2_000L, 9_000L)
        assertEquals(9_000L, start)
        assertEquals(2_000L, store.get())
    }

    @Test
    fun savedStartInTheFutureIsIgnored() {
        val store = Fake(10_000L)
        val start = DischargeAnchor.elapsedStart(store, 5_000L, 7_000L)
        assertEquals(7_000L, start)
        assertEquals(5_000L, store.get())
    }

    @Test
    fun neverReturnsZero() {
        val store = Fake(100L)
        assertNotEquals(0L, DischargeAnchor.elapsedStart(store, 100L, 0L))
    }

    @Test
    fun chargerClearsAndUnplugStartsOnce() {
        val store = Fake()
        DischargeAnchor.observe(store, false, 1_000L)
        DischargeAnchor.observe(store, false, 5_000L)
        assertEquals(1_000L, store.get())
        DischargeAnchor.observe(store, null, 6_000L)
        assertEquals(1_000L, store.get())
        DischargeAnchor.observe(store, true, 7_000L)
        assertNull(store.get())
    }
}
