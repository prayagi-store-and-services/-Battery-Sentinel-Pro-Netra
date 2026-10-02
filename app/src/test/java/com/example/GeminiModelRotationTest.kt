package com.example

import com.example.ai.GeminiModelRotation
import com.example.ai.GeminiModelRotation.Outcome
import com.example.ai.GeminiModelRotation.RawResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeminiModelRotationTest {
    @Before fun reset() = GeminiModelRotation.resetForTests()

    @Test fun rotatesToNextModelOn429() {
        val tried = mutableListOf<String>()
        val out = GeminiModelRotation.run(listOf("a", "b", "c")) { m ->
            tried += m
            if (m == "a") RawResponse(429, "") else RawResponse(200, "ok-$m")
        }
        assertEquals(listOf("a", "b"), tried)
        assertEquals(Outcome.Success("b", "ok-b"), out)
    }

    @Test fun rotatesOnResourceExhaustedBodyAndReturnsExhaustedWhenAllFail() {
        val out = GeminiModelRotation.run(listOf("a", "b")) { RawResponse(503, "RESOURCE_EXHAUSTED quota") }
        assertEquals(Outcome.QuotaExhausted, out)
    }

    @Test fun nonQuotaErrorDoesNotRotate() {
        val tried = mutableListOf<String>()
        val out = GeminiModelRotation.run(listOf("a", "b")) { m -> tried += m; RawResponse(400, "bad request") }
        assertEquals(listOf("a"), tried)
        assertEquals(Outcome.Failed("a", 400), out)
    }

    @Test fun modelInCooldownIsSkippedThenRetriedAfterCooldown() {
        var t = 0L
        GeminiModelRotation.run(listOf("a", "b"), now = { t }, cooldownMs = 1000) { m ->
            if (m == "a") RawResponse(429, "") else RawResponse(200, "x")
        }
        val tried = mutableListOf<String>()
        t = 500
        GeminiModelRotation.run(listOf("a", "b"), now = { t }, cooldownMs = 1000) { m -> tried += m; RawResponse(200, "x") }
        assertEquals(listOf("b"), tried)
        tried.clear(); t = 1500
        GeminiModelRotation.run(listOf("a", "b"), now = { t }, cooldownMs = 1000) { m -> tried += m; RawResponse(200, "x") }
        assertEquals(listOf("a"), tried)
    }
}
