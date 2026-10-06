package com.example.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadMeterTest {
    @Test fun percentCountsUpAndEtaNeverRises() {
        val m = DownloadMeter()
        val total = 10_000_000L
        var lastPct = -1
        var lastEta = Long.MAX_VALUE
        var t = 0L
        var done = 0L
        val speeds = listOf(2_000_000L, 2_000_000L, 500_000L, 500_000L, 3_000_000L, 1_000_000L)
        for (s in speeds) {
            t += 1000L; done += s
            val line = m.line(done, total, t)
            val pct = Regex("^(\\d+)% downloaded").find(line)!!.groupValues[1].toInt()
            assertTrue(pct >= lastPct); lastPct = pct
            val secs = Regex("time left: (\\d+) s").find(line)?.groupValues?.get(1)?.toLong()
            if (secs != null) { assertTrue(secs <= lastEta); lastEta = secs }
        }
    }
    @Test fun helpers() {
        assertEquals(25, DownloadMeter.percent(25, 100))
        assertEquals("Unavailable", DownloadMeter.eta(null))
        assertEquals("1 min 5 s", DownloadMeter.eta(65))
    }
}
