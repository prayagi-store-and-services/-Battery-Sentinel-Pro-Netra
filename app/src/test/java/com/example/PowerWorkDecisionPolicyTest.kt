package com.example

import com.example.service.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class PowerWorkDecisionPolicyTest {
    private val current = PowerWorkContext(true, true, 100L, 101L, false, false, true, true)
    private fun reason(c: PowerWorkContext, importance: PowerWorkImportance = PowerWorkImportance.OPTIONAL) =
        PowerWorkDecisionPolicy.evaluate(importance, c).reason

    @Test fun optionalAndDecorativeNeverAcquireAnUnmeasuredBudget() {
        for (importance in listOf(PowerWorkImportance.OPTIONAL, PowerWorkImportance.DECORATIVE)) {
            assertFalse(UnverifiedPowerWorkBudget.permits(importance))
            val d = PowerWorkDecisionPolicy.evaluate(importance, current)
            assertEquals(PowerWorkDisposition.DEFER, d.disposition)
            assertEquals(PowerWorkReason.BUDGET_NOT_VERIFIED, d.reason)
            assertFalse(d.allowedByPowerPolicy)
        }
    }
    @Test fun essentialEligibilityDoesNotDependOnOptionalContext() {
        val unavailable = PowerWorkContext(false, false, null, -1L, true, null, false, false)
        val d = PowerWorkDecisionPolicy.evaluate(PowerWorkImportance.ESSENTIAL, unavailable)
        assertTrue(d.allowedByPowerPolicy)
        assertTrue(UnverifiedPowerWorkBudget.permits(PowerWorkImportance.ESSENTIAL))
        assertEquals(PowerWorkReason.ESSENTIAL_OPPORTUNITY, d.reason)
    }
    @Test fun missingBatteryIsUnavailable() {
        assertEquals(PowerWorkReason.STATE_UNAVAILABLE, reason(current.copy(batteryLevelAvailable = false)))
    }
    @Test fun absentStaleInvalidOrFutureMonotonicProvenanceDefers() {
        for (c in listOf(current.copy(stateFresh = false), current.copy(observedAtElapsedMillis = null),
            current.copy(observedAtElapsedMillis = -1), current.copy(nowElapsedMillis = -1),
            current.copy(observedAtElapsedMillis = 102))) {
            assertEquals(PowerWorkReason.STALE_OR_UNORDERED_STATE, reason(c))
        }
    }
    @Test fun equalClockAndLargeElapsedValuesDoNotInventAnAgeBudget() {
        assertEquals(PowerWorkReason.BUDGET_NOT_VERIFIED, reason(current.copy(observedAtElapsedMillis = 0, nowElapsedMillis = 0)))
        assertEquals(PowerWorkReason.BUDGET_NOT_VERIFIED, reason(current.copy(nowElapsedMillis = Long.MAX_VALUE)))
        // No subtraction, overflow, age threshold or fabricated freshness promise.
    }
    @Test fun existingRestrictionDefersWithoutRecomputingModes() {
        assertEquals(PowerWorkReason.EXISTING_RESTRICTION, reason(current.copy(existingRestrictionActive = true)))
    }
    @Test fun dozeAndUnknownDozeBothDefer() {
        for (doze in listOf(true, null)) assertEquals(PowerWorkReason.DOZE_OR_UNKNOWN, reason(current.copy(inDoze = doze)))
    }
    @Test fun decorationRequiresVisibleScreenWithoutGrantingOptionalWork() {
        for (c in listOf(current.copy(appVisible = false), current.copy(screenOn = false))) {
            assertEquals(PowerWorkReason.NOT_VISIBLE, reason(c, PowerWorkImportance.DECORATIVE))
            assertEquals(PowerWorkReason.BUDGET_NOT_VERIFIED, reason(c))
        }
    }
    @Test fun allBooleanAndDozeCombinationsRemainFailClosedForNonEssentialWork() {
        for (available in listOf(false, true)) for (fresh in listOf(false, true))
        for (restricted in listOf(false, true)) for (doze in listOf(false, true, null))
        for (visible in listOf(false, true)) for (screen in listOf(false, true)) {
            val c = current.copy(batteryLevelAvailable = available, stateFresh = fresh,
                existingRestrictionActive = restricted, inDoze = doze, appVisible = visible, screenOn = screen)
            for (importance in PowerWorkImportance.values()) {
                assertEquals(importance == PowerWorkImportance.ESSENTIAL,
                    PowerWorkDecisionPolicy.evaluate(importance, c).allowedByPowerPolicy)
            }
        }
    }
    @Test fun parallelCallsAreDeterministicAndDoNotMutateInput() {
        val before = current.copy()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll((1..100).map { Callable {
                PowerWorkDecisionPolicy.evaluate(PowerWorkImportance.OPTIONAL, current)
            } }).map { it.get() }
            assertEquals(1, results.toSet().size)
            assertEquals(before, current)
        } finally { pool.shutdownNow() }
    }
}
