package com.example

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class NotificationAccessRemovalTest {
    private fun source(path: String) = File("src/main/$path").readText()
    @Test fun manifestDoesNotExposeSensitiveNotificationOrSmsOrAccessibilityAccess() {
        val manifest = source("AndroidManifest.xml")
        listOf("BIND_NOTIFICATION_LISTENER_SERVICE", "NotificationListenerService", "BIND_ACCESSIBILITY_SERVICE", "READ_SMS", "RECEIVE_SMS").forEach {
            assertFalse("Manifest still declares $it",manifest.contains(it))
        }
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
    }
    @Test fun removedFeatureHasNoRuntimeOrUiPath() {
        val saver = source("java/com/example/service/SaverEngine.kt")
        val card = source("java/com/example/ui/components/SaverCard.kt")
        assertFalse(saver.contains("SaverNotificationListener"))
        assertFalse(saver.contains("hasNotificationAccess"))
        assertFalse(card.contains("ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        assertFalse(card.contains("KEY_ACT_NOTIF"))
        assertFalse(File("src/main/java/com/example/service/SaverNotificationListener.kt").exists())
        assertFalse(File("src/main/java/com/example/ui/components/RestrictedSettingsGuide.kt").exists())
    }
}
