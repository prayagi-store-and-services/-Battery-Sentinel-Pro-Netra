package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.example.service.StabilityDiagnosticPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = NetraApplication::class)
class StabilityStartupAndPrivacyTest {
    @Test fun applicationStartupInitializesWorkManagerOnDemand() {
        val app = ApplicationProvider.getApplicationContext<NetraApplication>()
        assertNotNull(app.stabilitySentinel)
        assertNotNull(WorkManager.getInstance(app))
        assertNotNull(app.workManagerConfiguration)
    }
    @Test fun onlyHttpsEndpointWithoutCredentialsOrQueryIsAccepted() {
        assertTrue(StabilityDiagnosticPolicy.isSecureEndpoint("https://reports.example.invalid/incidents"))
        for (url in listOf("http://reports.example.invalid", "file:///tmp/report", "https://user:secret@example.invalid", "https://example.invalid?token=secret", "broken")) {
            assertFalse(StabilityDiagnosticPolicy.isSecureEndpoint(url))
        }
    }
    @Test fun transmittedFramesNeverContainMessagesOrFilePaths() {
        val error = IllegalStateException("password=supersecret /private/data user@example.invalid")
        error.stackTrace = arrayOf(
            StackTraceElement("com.example.service.BatteryMonitorService", "observe", "/secret/path/password", 42),
            StackTraceElement("outside.UserData", "secret", "private", 1))
        val stack = StabilityDiagnosticPolicy.safeStack(error)
        assertEquals("com.example.service.BatteryMonitorService.observe:42", stack)
        assertFalse(stack.contains("supersecret"))
        assertFalse(stack.contains("/secret"))
        assertFalse(stack.contains("UserData"))
    }
}
