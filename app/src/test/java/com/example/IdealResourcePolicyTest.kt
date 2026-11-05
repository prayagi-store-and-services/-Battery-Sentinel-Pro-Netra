package com.example

import com.example.service.IdealResourcePolicy
import org.junit.Assert.*
import org.junit.Test

class IdealResourcePolicyTest {
    @Test fun missingCpuNeverCertifiesIdealEvenWithEnoughRam() {
        assertFalse(IdealResourcePolicy.satisfied(80f, null))
    }
    @Test fun missingRamNeverCertifiesIdeal() {
        assertFalse(IdealResourcePolicy.satisfied(null, 100f))
    }
    @Test fun validThresholdsAreInclusive() {
        assertTrue(IdealResourcePolicy.satisfied(40f, 60f))
        assertTrue(IdealResourcePolicy.satisfied(100f, 100f))
        assertFalse(IdealResourcePolicy.satisfied(39.9f, 100f))
        assertFalse(IdealResourcePolicy.satisfied(80f, 59.9f))
    }
    @Test fun nonFiniteAndImpossibleMeasurementsAreRejected() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 101f)) {
            assertFalse(IdealResourcePolicy.satisfied(bad, 100f))
            assertFalse(IdealResourcePolicy.satisfied(100f, bad))
        }
    }
}
