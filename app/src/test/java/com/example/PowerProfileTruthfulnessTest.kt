package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.PowerProfileManager
import com.example.model.BatteryTelemetry
import com.example.model.PowerProfileMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PowerProfileTruthfulnessTest {
    @Test fun preferencesNeverClaimUnwiredControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PowerProfileManager(context)
        PowerProfileMode.entries.forEach { mode ->
            manager.setPowerProfile(mode, BatteryTelemetry(level = 8, isDataAvailable = true))
            assertFalse(manager.profileState.value.dynamicSyncThrottled)
            assertFalse(manager.profileState.value.backgroundSyncPaused)
            assertNull(manager.profileState.value.adaptiveBrightnessSuggested)
        }
    }
    @Test fun unavailableBatteryNeverTriggersCriticalSuggestion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PowerProfileManager(context)
        manager.setPowerProfile(PowerProfileMode.SMART_ADAPTIVE, BatteryTelemetry())
        assertEquals(PowerProfileMode.BALANCED, manager.profileState.value.activeEffectiveMode)
        assertTrue(manager.profileState.value.lastProfileTransitionReason.contains("unavailable"))
    }
}
