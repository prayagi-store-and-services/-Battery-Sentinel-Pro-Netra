package com.example.service

import android.os.Process
import android.os.SystemClock

/** Real CPU time used by this app's own process since it started. Measured, not estimated. */
object SelfMeter {
    data class Reading(val cpuMs: Long, val runMs: Long) {
        val percent: Double get() = if (runMs <= 0L) 0.0 else cpuMs * 100.0 / runMs
    }

    fun read(): Reading {
        val run = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        return Reading(Process.getElapsedCpuTime(), run)
    }

    fun describe(r: Reading): String {
        val min = r.runMs / 60_000L
        return "${r.cpuMs / 1000L} s CPU in ${min} min running (${"%.2f".format(r.percent)} percent of one core)"
    }
}
