package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.model.BatteryTelemetry
import com.example.ui.components.BatteryHealthTrendLineChart
import com.example.ui.components.NightChargingThrottleCard
import com.example.ui.components.ThermalAppCorrelationHeatmap
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** CPU-rendered Compose evidence, uploaded by CI for visual review before UI merges. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
class TruthfulnessCardRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderUnavailableHealthAndThermalCards() {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.width(380.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    BatteryHealthTrendLineChart(emptyList())
                    ThermalAppCorrelationHeatmap(emptyList(), emptyList(), Modifier.padding(top = 20.dp))
                    NightChargingThrottleCard(
                        telemetry = BatteryTelemetry(level = 80, isCharging = true),
                        isFeatureEnabled = true, targetWakeHour = 7,
                        records = emptyList(), sessions = emptyList(),
                        onToggleFeature = {}, onSelectWakeHour = {},
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }
        }
        compose.onNodeWithText("Capacity health trend unavailable").assertIsDisplayed()
        compose.onNodeWithText("App/thermal correlation unavailable").assertIsDisplayed()
        compose.onNodeWithText("Night charging control unavailable").assertIsDisplayed()
    }
}
