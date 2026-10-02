package com.example.ai

/**
 * Rotates across Gemini models when one hits its own quota (HTTP 429 / RESOURCE_EXHAUSTED).
 * Each model has a separate quota, so the next model is tried before any local fallback.
 * A model that just hit quota is skipped for [cooldownMs] so we do not keep hammering it.
 */
object GeminiModelRotation {
    const val DEFAULT_COOLDOWN_MS = 2 * 60 * 1000L

    data class RawResponse(val code: Int, val body: String)

    sealed class Outcome {
        data class Success(val model: String, val body: String) : Outcome()
        /** Every model was in cooldown or returned a quota error. */
        object QuotaExhausted : Outcome()
        /** A non-quota failure (auth, bad request, server error); caller falls back locally. */
        data class Failed(val model: String, val code: Int) : Outcome()
    }

    private val cooldownUntil = HashMap<String, Long>()

    @Synchronized
    private fun isCoolingDown(model: String, now: Long) = (cooldownUntil[model] ?: 0L) > now

    @Synchronized
    private fun markQuota(model: String, now: Long, cooldownMs: Long) {
        cooldownUntil[model] = now + cooldownMs
    }

    @Synchronized
    fun resetForTests() = cooldownUntil.clear()

    fun isQuotaError(r: RawResponse): Boolean =
        r.code == 429 || (r.code != 200 && (r.body.contains("RESOURCE_EXHAUSTED") ||
            r.body.contains("quota", ignoreCase = true)))

    /**
     * Tries [models] in order. Returns the first successful response, or [Outcome.QuotaExhausted]
     * when every model is in cooldown or over quota.
     */
    fun run(
        models: List<String>,
        now: () -> Long = System::currentTimeMillis,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        call: (model: String) -> RawResponse
    ): Outcome {
        for (model in models) {
            if (isCoolingDown(model, now())) continue
            val r = call(model)
            when {
                r.code in 200..299 -> return Outcome.Success(model, r.body)
                isQuotaError(r) -> markQuota(model, now(), cooldownMs)
                else -> return Outcome.Failed(model, r.code)
            }
        }
        return Outcome.QuotaExhausted
    }
}
