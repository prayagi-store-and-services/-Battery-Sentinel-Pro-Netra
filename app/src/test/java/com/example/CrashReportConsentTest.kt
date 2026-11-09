package com.example

import com.example.service.StabilitySentinel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Crash-report consent rules (owner rule 9 Nov 2026): crash reports stay on the device until the user
 * reviews them and presses Send; only health/incident telemetry may auto-flush. UI send/cancel/delete
 * flows need a device or emulator and are NOT covered here.
 */
class CrashReportConsentTest {
    @Test fun crashFilesAreUserReviewOnly() {
        assertTrue(StabilitySentinel.isUserReviewOnlyReport("crash-1731000000000-abc.json"))
    }
    @Test fun incidentAndHealthFilesMayAutoFlush() {
        assertFalse(StabilitySentinel.isUserReviewOnlyReport("incident-fp123-1731000000000.json"))
        assertFalse(StabilitySentinel.isUserReviewOnlyReport("health-1731000000000.json"))
    }
    @Test fun noStartupAutoSendPathRemains() {
        // Structural guard: the old automatic startup sender must not come back.
        val methods = Class.forName("com.example.util.CrashAutoSender").declaredMethods.map { it.name }
        assertFalse(methods.contains("sendPending"))
    }
}
