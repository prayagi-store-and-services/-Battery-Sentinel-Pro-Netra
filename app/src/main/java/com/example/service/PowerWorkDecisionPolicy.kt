package com.example.service

/** Classification of work, not permission to run any particular feature. */
internal enum class PowerWorkImportance { ESSENTIAL, OPTIONAL, DECORATIVE }
internal enum class PowerWorkDisposition { ALLOW_AT_EXISTING_OPPORTUNITY, DEFER }
internal enum class PowerWorkReason {
    ESSENTIAL_OPPORTUNITY, STATE_UNAVAILABLE, STALE_OR_UNORDERED_STATE,
    EXISTING_RESTRICTION, DOZE_OR_UNKNOWN, NOT_VISIBLE, BUDGET_NOT_VERIFIED
}

/** Immutable input only. No clock is read, state published or numerical budget invented here. */
internal data class PowerWorkContext(
    val batteryLevelAvailable: Boolean,
    val stateFresh: Boolean,
    val observedAtElapsedMillis: Long?,
    val nowElapsedMillis: Long,
    val existingRestrictionActive: Boolean,
    val inDoze: Boolean?,
    val appVisible: Boolean,
    val screenOn: Boolean,
)

internal data class PowerWorkDecision(
    val disposition: PowerWorkDisposition,
    val reason: PowerWorkReason,
) {
    /** ALLOW is a power-policy result only, never consent, permission or OS eligibility. */
    val allowedByPowerPolicy: Boolean
        get() = disposition == PowerWorkDisposition.ALLOW_AT_EXISTING_OPPORTUNITY
}

/** No measured finite optional budget is available. There is deliberately no bypass setter. */
internal object UnverifiedPowerWorkBudget {
    fun permits(importance: PowerWorkImportance): Boolean = importance == PowerWorkImportance.ESSENTIAL
}

/**
 * Stateless Guardian-to-BSPN contract. Unused by production consumers in Slice1.
 * No mode reduction, safety threshold, schedule, retry, storage or enforcement is added.
 * Caller freshness is evidence, not a maximum-age guarantee supplied by this contract.
 */
internal object PowerWorkDecisionPolicy {
    fun evaluate(importance: PowerWorkImportance, context: PowerWorkContext): PowerWorkDecision {
        fun defer(reason: PowerWorkReason) = PowerWorkDecision(PowerWorkDisposition.DEFER, reason)
        if (importance == PowerWorkImportance.ESSENTIAL) {
            return PowerWorkDecision(PowerWorkDisposition.ALLOW_AT_EXISTING_OPPORTUNITY,
                PowerWorkReason.ESSENTIAL_OPPORTUNITY)
        }
        if (!context.batteryLevelAvailable) return defer(PowerWorkReason.STATE_UNAVAILABLE)
        val observed = context.observedAtElapsedMillis
        if (!context.stateFresh || observed == null || observed < 0L ||
            context.nowElapsedMillis < 0L || context.nowElapsedMillis < observed) {
            return defer(PowerWorkReason.STALE_OR_UNORDERED_STATE)
        }
        if (context.existingRestrictionActive) return defer(PowerWorkReason.EXISTING_RESTRICTION)
        if (context.inDoze != false) return defer(PowerWorkReason.DOZE_OR_UNKNOWN)
        if (importance == PowerWorkImportance.DECORATIVE && (!context.appVisible || !context.screenOn)) {
            return defer(PowerWorkReason.NOT_VISIBLE)
        }
        // A mode window is never evidence that a finite measured budget exists.
        check(!UnverifiedPowerWorkBudget.permits(importance))
        return defer(PowerWorkReason.BUDGET_NOT_VERIFIED)
    }
}
