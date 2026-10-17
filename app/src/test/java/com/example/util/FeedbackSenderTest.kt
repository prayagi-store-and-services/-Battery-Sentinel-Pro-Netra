package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSenderTest {
    @Test fun payloadAlwaysNamesTheApp() {
        val p = FeedbackPayload(
            kind = "feedback",
            deviceModel = "V2420",
            androidVersion = "16",
            appVersion = "2.1.23",
            message = "test",
            stackTrace = null
        )
        assertEquals("Battery Sentinel Pro Netra", p.fields()["app"])
        assertTrue(p.disclosure().contains("App: Battery Sentinel Pro Netra"))
    }

    @Test fun acceptedNeedsRealSuccessAnswer() {
        assertTrue(FeedbackSender.accepted("{\"success\":\"true\",\"message\":\"ok\"}"))
        assertTrue(FeedbackSender.accepted("{\"success\":true}"))
        assertFalse(FeedbackSender.accepted("{\"success\":\"false\",\"message\":\"Needs Activation\"}"))
        assertFalse(FeedbackSender.accepted(""))
    }
}
