package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSenderTest {
    @Test fun acceptedNeedsRealSuccessAnswer() {
        assertTrue(FeedbackSender.accepted("{\"success\":\"true\",\"message\":\"ok\"}"))
        assertTrue(FeedbackSender.accepted("{\"success\":true}"))
        assertFalse(FeedbackSender.accepted("{\"success\":\"false\",\"message\":\"Needs Activation\"}"))
        assertFalse(FeedbackSender.accepted(""))
    }
}
