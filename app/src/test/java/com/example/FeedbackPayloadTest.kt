package com.example

import com.example.util.FeedbackBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackPayloadTest {
    @Test fun feedbackCarriesOnlyTheAllowedFields() {
        val f = FeedbackBuilder.feedback("Model X", "14", "1.1.2", "  hello  ").fields()
        assertEquals(setOf("_subject", "_captcha", "_template", "device", "android_version", "app_version", "message"), f.keys)
        assertEquals("hello", f["message"])
        assertEquals("[Netra] In-app Feedback", f["_subject"])
    }

    @Test fun crashCarriesTraceAndNoMessage() {
        val f = FeedbackBuilder.crash("Model X", "14", "1.1.2", "java.lang.Boom\n  at a.B.c(B.kt:1)").fields()
        assertTrue(f.containsKey("stack_trace"))
        assertFalse(f.containsKey("message"))
    }

    @Test fun stackTraceDropsExceptionMessages() {
        val t = IllegalStateException("secret user text 12345", RuntimeException("inner secret"))
        val s = FeedbackBuilder.sanitizeStackTrace(t)
        assertTrue(s.contains("java.lang.IllegalStateException"))
        assertFalse(s.contains("secret"))
    }

    @Test fun messageAndTraceAreLengthLimited() {
        assertEquals(FeedbackBuilder.MAX_MESSAGE, FeedbackBuilder.feedback("m", "1", "v", "x".repeat(5000)).message!!.length)
        assertEquals(FeedbackBuilder.MAX_TRACE, FeedbackBuilder.crash("m", "1", "v", "y".repeat(9000)).stackTrace!!.length)
    }

    @Test fun disclosureListsExactlyWhatIsSent() {
        val d = FeedbackBuilder.feedback("Model X", "14", "1.1.2", "hi").disclosure()
        assertTrue(d.contains("Model X") && d.contains("14") && d.contains("1.1.2") && d.contains("hi"))
        assertTrue(d.contains("Nothing else is sent"))
    }
}
