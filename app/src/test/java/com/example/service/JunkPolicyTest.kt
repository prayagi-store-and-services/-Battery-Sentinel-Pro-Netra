package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkPolicyTest {
    private val mb = 1024L * 1024L
    private val h = 3600_000L

    @Test fun unreadableSizeNeverWarns() = assertFalse(JunkPolicy.shouldWarn(null, 1000L, 0L, 0L))
    @Test fun underLimitNeverWarns() = assertFalse(JunkPolicy.shouldWarn(199 * mb, 1000L, 0L, 0L))
    @Test fun firstCrossingWarns() = assertTrue(JunkPolicy.shouldWarn(200 * mb, 1000L, 0L, 0L))
    @Test fun twoGbUnseenBeforeNowWarns() = assertTrue(JunkPolicy.shouldWarn(2048 * mb, 5L, 0L, 0L))
    @Test fun noRepeatWithinTwelveHours() = assertFalse(JunkPolicy.shouldWarn(300 * mb, 11 * h, 1L, 250 * mb))
    @Test fun repeatsAfterTwelveHours() = assertTrue(JunkPolicy.shouldWarn(300 * mb, 13 * h, 1L, 250 * mb))
    @Test fun repeatsWhenItGrowsFiveHundredMb() = assertTrue(JunkPolicy.shouldWarn(800 * mb, 2 * h, 1L, 250 * mb))
    @Test fun clearsOnlyOnRealLowReading() {
        assertTrue(JunkPolicy.shouldClear(10 * mb))
        assertFalse(JunkPolicy.shouldClear(null))
        assertFalse(JunkPolicy.shouldClear(200 * mb))
    }
}
