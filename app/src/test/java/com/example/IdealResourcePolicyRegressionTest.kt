package com.example

import com.example.model.NetraCentralState
import com.example.service.IdealResourcePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the CPU-headroom truthfulness fix (#291):
 * unknown CPU is never "ideal" and never raises a CPU-optimization warning.
 */
class IdealResourcePolicyRegressionTest {
    @Test fun nullCpuHeadroomIsNotIdeal() {
        assertFalse(IdealResourcePolicy.satisfied(80f, null))
    }
    @Test fun belowSixtyCpuHeadroomIsNotIdeal() {
        assertFalse(IdealResourcePolicy.satisfied(80f, 59.9f))
    }
    @Test fun sixtyPlusWithValidRamMayQualify() {
        assert(IdealResourcePolicy.satisfied(40f, 60f))
        assert(IdealResourcePolicy.satisfied(100f, 100f))
    }
    @Test fun nonFiniteAndOutOfRangeAreNotIdeal() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 101f)) {
            assertFalse(IdealResourcePolicy.satisfied(80f, bad))
            assertFalse(IdealResourcePolicy.satisfied(bad, 80f))
        }
    }
    @Test fun unknownCpuNeverRaisesCpuOptimizationWarning() {
        // Defaults after the fix: no CPU measurement -> null headroom, no warning.
        val state = NetraCentralState()
        assertNull(state.cpuHeadroomPercent)
        assertFalse(state.isCpuOptimizationNeeded)
    }
}
