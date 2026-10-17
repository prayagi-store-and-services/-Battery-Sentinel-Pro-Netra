package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemAutostartHelperTest {

    @Test fun affectedBrandsDetectedCaseInsensitively() {
        assertTrue(OemAutostartHelper.needsAttention("realme"))
        assertTrue(OemAutostartHelper.needsAttention("Xiaomi"))
        assertTrue(OemAutostartHelper.needsAttention(" vivo "))
        assertTrue(OemAutostartHelper.needsAttention("OPPO"))
    }

    @Test fun unaffectedBrandsNotFlagged() {
        assertFalse(OemAutostartHelper.needsAttention("samsung"))
        assertFalse(OemAutostartHelper.needsAttention("Google"))
        assertFalse(OemAutostartHelper.needsAttention("motorola"))
        assertFalse(OemAutostartHelper.needsAttention(""))
    }

    @Test fun firstResolvablePicksKnownComponentInOrder() {
        val r = OemAutostartHelper.firstResolvable(setOf("com.oppo.safe", "com.miui.securitycenter"))
        assertEquals("com.oppo.safe", r?.first)
    }

    @Test fun firstResolvableNullWhenNothingResolves() {
        assertNull(OemAutostartHelper.firstResolvable(emptySet()))
        assertNull(OemAutostartHelper.firstResolvable(setOf("com.unrelated.app")))
    }
}
