package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.ui.components.SentinelCard
import com.example.ui.theme.NetraEmerald

/** Same production Settings card, extracted for both real states' visual verification. */
@Composable
internal fun AndroidBatterySettingsCard(isIgnoringBatteryOptimizations: Boolean, onReviewSettings: () -> Unit) {
            SentinelCard(
                title = "Android Battery Settings (Optional)",
                icon = Icons.Default.Shield,
                dotState = if (isIgnoringBatteryOptimizations) DotState.CONNECTED else DotState.THROTTLED,
                accentColor = NetraEmerald
            ) {
                Text(
                    text = if (isIgnoringBatteryOptimizations)
                        "Android reports this app is not battery optimized. Doze restrictions and manufacturer limits may still apply; continuous monitoring is not guaranteed."
                    else
                        "Android battery optimization is enabled. You can review the system settings if monitoring is delayed. Changing them is optional and may increase battery use; the app cannot prevent Android from stopping it.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!isIgnoringBatteryOptimizations) {
                    OutlinedButton(
                        onClick = onReviewSettings,
                        modifier = Modifier.fillMaxWidth().testTag("exempt_battery_button")
                    ) {
                        Text("Review Android Battery Settings", fontSize = 12.sp)
                    }
                }
            }
}
