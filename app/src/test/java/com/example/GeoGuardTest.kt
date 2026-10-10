package com.example

import com.example.geo.GeoGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoGuardTest {
    @Test fun blocksListedSimCountry() {
        assertTrue(GeoGuard.decide("pk", "", "IN"))
        assertTrue(GeoGuard.decide("BD", null, null))
        assertTrue(GeoGuard.decide("af", "af", "US"))
        assertTrue(GeoGuard.decide("cn", null, null))
        assertTrue(GeoGuard.decide("", "kp", null))
    }
    @Test fun indianSimNeverBlocks() {
        assertFalse(GeoGuard.decide("in", "pk", "PK"))
    }
    @Test fun failsOpenWithoutSignal() {
        assertFalse(GeoGuard.decide(null, null, null))
        assertFalse(GeoGuard.decide("", "", ""))
    }
    @Test fun localeOnlyUsedWhenNoTelephonySignal() {
        assertTrue(GeoGuard.decide("", "", "PK"))
        assertFalse(GeoGuard.decide("us", "", "PK"))
    }
    @Test fun listIsExtendable() {
        assertTrue(GeoGuard.decide("xx", null, null, setOf("XX")))
        assertEquals(setOf("PK", "BD", "AF", "CN", "KP"), GeoGuard.BLOCKED)
    }
}
