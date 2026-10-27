package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.service.EmergencySaverPlan
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.util.HeatSuspects

/**
 * Emergency Saver card + full-screen planner.
 * The user picks phone brand, network need and usage, sets battery percent and hours to
 * cover, and gets an exact ordered plan: what the app applies itself (brightness and
 * screen timeout, with the Modify-system-settings switch) and what only Android's own
 * Settings can do (each with a button that opens the right screen). The per-hour budget
 * is a target from the given numbers, never a promise.
 */
@Composable
fun EmergencySaverCard(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var restoreStatus by remember { mutableStateOf("") }

    SentinelCard(
        title = "Emergency Saver",
        icon = Icons.Default.BatterySaver,
        dotState = DotState.STANDBY,
        accentColor = StatusAmber
    ) {
        Text(
            text = "Low battery, hours to cover, phone warm? Build an exact saving plan for this phone.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { open = true },
            colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
            modifier = Modifier.testTag("emergency_saver_open_button")
        ) {
            Text("Build my saving plan", fontSize = 13.sp)
        }
        Button(onClick = {
            com.example.service.SavingSettings.restore(context, "emergency")
            restoreStatus = com.example.service.SavingSettings.status(context)
        }) { Text("End emergency display savings / retry restore") }
        if (restoreStatus.isNotEmpty()) Text(restoreStatus, fontSize = 11.sp)
    }

    if (open) {
        EmergencySaverOverlay(onClose = { open = false })
    }
}

@Composable
private fun EmergencySaverOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    var brand by remember { mutableStateOf(EmergencySaverPlan.Brand.OTHER) }
    var network by remember { mutableStateOf(EmergencySaverPlan.NetworkNeed.MOBILE_DATA) }
    var usage by remember { mutableStateOf(EmergencySaverPlan.Usage.NORMAL) }
    var percent by remember { mutableStateOf(30f) }
    var hours by remember { mutableStateOf(6f) }
    var applied by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    val canWrite = remember { Settings.System.canWrite(context) }
    val budget = EmergencySaverPlan.budget(percent.toInt(), hours.toInt())
    val heavyApps = remember {
        try { HeatSuspects.find(context, null, requireHot = false).map { it.appName } } catch (_: Exception) { emptyList() }
    }
    val steps = EmergencySaverPlan.plan(brand, network, usage, canWrite, heavyApps)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetraDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Emergency Saver plan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NetraCyan)
                Text(
                    text = "Close",
                    fontSize = 14.sp,
                    color = StatusAmber,
                    modifier = Modifier
                        .testTag("emergency_saver_close")
                        .clickable { onClose() }
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Battery now: ${percent.toInt()}%   Hours to cover: ${hours.toInt()}h", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Slider(value = percent, onValueChange = { percent = it }, valueRange = 5f..100f, modifier = Modifier.testTag("emergency_percent_slider"))
            Slider(value = hours, onValueChange = { hours = it }, valueRange = 1f..12f, modifier = Modifier.testTag("emergency_hours_slider"))

            Text(
                text = "Budget (target, not a promise): keep ${budget.reservePercent}% in reserve, so ${budget.usablePercent}% over ${budget.hours}h = about ${String.format(java.util.Locale.US, "%.1f", budget.targetPerHour)}% per hour. Real drain depends on the phone, network and apps.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("Your phone brand", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            ChipRow(
                options = EmergencySaverPlan.Brand.entries.map { it.label },
                selectedIndex = EmergencySaverPlan.Brand.entries.indexOf(brand),
                onSelect = { brand = EmergencySaverPlan.Brand.entries[it] },
                tagPrefix = "brand"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Network you need", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            ChipRow(
                options = EmergencySaverPlan.NetworkNeed.entries.map { it.label },
                selectedIndex = EmergencySaverPlan.NetworkNeed.entries.indexOf(network),
                onSelect = { network = EmergencySaverPlan.NetworkNeed.entries[it] },
                tagPrefix = "network"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Usage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            ChipRow(
                options = EmergencySaverPlan.Usage.entries.map { it.label },
                selectedIndex = EmergencySaverPlan.Usage.entries.indexOf(usage),
                onSelect = { usage = EmergencySaverPlan.Usage.entries[it] },
                tagPrefix = "usage"
            )

            Button(onClick = {
                com.example.service.SavingSettings.restore(context, "emergency")
                applied = false
                result = com.example.service.SavingSettings.status(context)
            }) { Text("End display savings / retry restore") }
            if (result.isNotEmpty()) Text(result, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Your plan, in order", fontSize = 14.sp, fontWeight = FontWeight.Bold)

            steps.forEachIndexed { i, step ->
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(10.dp)
                ) {
                    Text("${i + 1}. ${step.title}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(step.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (step.action) {
                        EmergencySaverPlan.StepAction.APPLY_IN_APP -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    applied = applyDisplaySavings(context)
                                    result = com.example.service.SavingSettings.status(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetraEmerald),
                                modifier = Modifier.testTag("emergency_apply_button")
                            ) { Text(if (applied) "Applied" else "Apply now", fontSize = 12.sp) }
                        }
                        EmergencySaverPlan.StepAction.OPEN_SETTINGS -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(step.settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetraCyan),
                                modifier = Modifier.testTag("emergency_open_settings_$i")
                            ) { Text("Open Settings", fontSize = 12.sp) }
                        }
                        EmergencySaverPlan.StepAction.GUIDE_ONLY -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("While saving", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            EmergencySaverPlan.usageGuide.forEach { Text("- $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Spacer(modifier = Modifier.height(8.dp))
            Text("If the phone is at 40°C or more", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            EmergencySaverPlan.heatSafetyTips.forEach { Text("- $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, tagPrefix: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) NetraDarkBg else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) NetraCyan else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("${tagPrefix}_chip_$i")
            )
        }
    }
}

/** Applies brightness 25% + 15s screen timeout. Returns true when actually applied. */
private fun applyDisplaySavings(context: android.content.Context): Boolean {
    return com.example.service.SavingSettings.display(context, "emergency", 64, 15_000)
}
