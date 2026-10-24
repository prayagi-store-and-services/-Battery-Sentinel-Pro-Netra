package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppUsageItem
import com.example.model.DotState
import com.example.ui.components.SentinelCard
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusRed
import com.example.util.BatteryHardwareFacts
import com.example.util.DrainWindowAnalyzer
import com.example.util.PermissionHelper
import com.example.util.UsageStatsHelper
import com.example.viewmodel.NetraViewModel

@Composable
fun AppBatteryUsageScreen(
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasUsagePermission by remember { mutableStateOf(PermissionHelper.isUsageAccessGranted(context)) }
    var appUsageList by remember { mutableStateOf<List<AppUsageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    fun refreshAppUsage() {
        isLoading = true
        hasUsagePermission = PermissionHelper.isUsageAccessGranted(context)
        if (hasUsagePermission) {
            appUsageList = UsageStatsHelper.getAppUsageDrainList(context)
        } else {
            appUsageList = emptyList()
        }
        isLoading = false
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshAppUsage()
                viewModel.refreshHardwareState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        refreshAppUsage()
    }

    var suspectsWindowHours by remember { mutableStateOf(3) }
    var suspects by remember { mutableStateOf<List<DrainWindowAnalyzer.Suspect>>(emptyList()) }
    var batteryFacts by remember { mutableStateOf<BatteryHardwareFacts.Facts?>(null) }

    fun refreshSuspects() {
        batteryFacts = BatteryHardwareFacts.read(context)
        if (PermissionHelper.isUsageAccessGranted(context)) {
            val end = System.currentTimeMillis()
            suspects = DrainWindowAnalyzer.suspects(context, end - suspectsWindowHours * 3600_000L, end)
        } else {
            suspects = emptyList()
        }
    }

    LaunchedEffect(suspectsWindowHours) { refreshSuspects() }

    AppForegroundUsageContent(appUsageList, hasUsagePermission, selectedCategoryFilter,
        { selectedCategoryFilter = it }, { refreshAppUsage(); refreshSuspects() },
        { UsageStatsHelper.openUsageAccessSettings(context) },
        { UsageStatsHelper.openAppDetailsSettings(context, it) }, modifier,
        suspects = suspects,
        suspectsWindowHours = suspectsWindowHours,
        onSelectSuspectsWindow = { suspectsWindowHours = it },
        batteryFacts = batteryFacts)
}

@Composable
internal fun AppForegroundUsageContent(
    appUsageList: List<AppUsageItem>, hasUsagePermission: Boolean,
    selectedCategoryFilter: String = "ALL", onSelectFilter: (String) -> Unit = {},
    onRefresh: () -> Unit = {}, onOpenUsageSettings: () -> Unit = {},
    onOpenAppSettings: (String) -> Unit = {}, modifier: Modifier = Modifier,
    suspects: List<DrainWindowAnalyzer.Suspect> = emptyList(),
    suspectsWindowHours: Int = 3,
    onSelectSuspectsWindow: (Int) -> Unit = {},
    batteryFacts: BatteryHardwareFacts.Facts? = null
) {
    val context = LocalContext.current
    val filteredList = if (!hasUsagePermission) emptyList() else if (selectedCategoryFilter == "ALL") appUsageList
        else appUsageList.filter { it.category.contains(selectedCategoryFilter, ignoreCase = true) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: App Usage & BatteryStats Analytics
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "App Foreground Usage",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Android foreground-time buckets for a recent-day query. Intervals may extend beyond 24h; per-app energy unavailable.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_app_usage_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Permission Warning Card if not granted
        if (!hasUsagePermission) {
            item {
                SentinelCard(
                    title = "Usage Access Permission Required",
                    icon = Icons.Default.Lock,
                    dotState = DotState.THROTTLED,
                    accentColor = StatusAmber
                ) {
                    Text(
                        text = "Android requires Usage Access permission to read application foreground time. It does not expose per-app battery energy here.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Tap below to open Android Settings.\n• Locate 'Netra Sentinel Pro' or 'Battery Sentinel Pro' in the list and toggle to ON.\n• (If toggle is greyed out on Android 13+, open App Info > 3 dots > 'Allow restricted settings').",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onOpenUsageSettings()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("grant_usage_permission_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Usage Access in Android Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Energy KPI Summary Banner
        // Android gives apps usage TIME, not measured battery per app, so the old "per-app energy" and "high drain" boxes
        // (always Unavailable) are gone. Only the real count is shown, and only when Usage Access is granted.
        if (hasUsagePermission) item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppKpiBox("TRACKED APPS", "${appUsageList.size} listed", NetraEmerald, Modifier.weight(1f))
            }
        }

        // Battery drop suspects: who was ACTIVE while the battery fell (activity time, never mAh)
        if (hasUsagePermission) item {
            SentinelCard(
                title = "Battery Drop Suspects",
                icon = Icons.Default.ElectricMeter,
                dotState = DotState.THROTTLED,
                accentColor = StatusAmber
            ) {
                Text(
                    text = "Android does not tell any app which app used how much battery - only the phone's own battery screen knows that. This list shows which apps were ACTIVE (on screen or running a background service) in the chosen window, so a fast drain can be matched to whoever was busy while it happened. Times are activity, not battery share.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "1h", 3 to "3h", 12 to "12h").forEach { (hours, label) ->
                        val selected = suspectsWindowHours == hours
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectSuspectsWindow(hours) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NetraCyan.copy(alpha = 0.25f),
                                selectedLabelColor = NetraCyan
                            ),
                            modifier = Modifier.testTag("suspects_window_$hours")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (suspects.isEmpty()) {
                    Text(
                        text = "No app activity found in this window. If the battery still fell fast with the screen off and no app active, the cause is more likely the battery or phone itself than an app.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    suspects.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = s.appName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = buildString {
                                    if (s.foregroundMinutes > 0) append("${s.foregroundMinutes}m on screen")
                                    if (s.foregroundMinutes > 0 && s.backgroundServiceMinutes > 0) append(" + ")
                                    if (s.backgroundServiceMinutes > 0) append("${s.backgroundServiceMinutes}m background")
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("open_system_battery_button")
                ) {
                    Text("Open phone's own battery usage screen", fontSize = 12.sp)
                }
            }
        }

        // Battery hardware facts (real sensor values; capacity in mAh is NOT exposed by Android)
        item {
            SentinelCard(
                title = "Battery Hardware Facts",
                icon = Icons.Default.Speed,
                dotState = DotState.CONNECTED,
                accentColor = NetraEmerald
            ) {
                if (batteryFacts == null) {
                    Text("Unavailable - the phone did not report battery readings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        text = "Health: ${batteryFacts.healthLabel}  |  ${batteryFacts.temperatureC?.let { "%.1f".format(it) + "°C" } ?: "Temperature unavailable"}  |  ${batteryFacts.voltageMv?.let { "$it mV" } ?: "Voltage unavailable"}  |  Level ${batteryFacts.levelPercent}%${if (batteryFacts.charging) " (charging)" else ""}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp)
                    )
                    Text(
                        text = "If the battery was replaced before and fast drain is back: a worn or low-quality replacement battery cannot be told apart from an app by any app - Android exposes no capacity (mAh) or cycle count. The honest test: if a fast drop window shows no active app above, suspect the battery; if one app is always busy, suspect the app.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to "All Apps",
                    "Media" to "Media",
                    "Social" to "Social",
                    "System" to "System"
                ).forEach { (key, label) ->
                    val isSelected = selectedCategoryFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFilter(key) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NetraCyan.copy(alpha = 0.25f),
                            selectedLabelColor = NetraCyan
                        ),
                        modifier = Modifier.testTag("app_filter_$key")
                    )
                }
            }
        }

        // App List
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasUsagePermission) "No application usage recorded in this filter." else "Usage Access required to display application foreground usage.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredList, key = { it.packageName }) { app ->
                AppDrainCard(app = app, onOpenSettings = {
                    onOpenAppSettings(app.packageName)
                })
            }
        }
    }
}

@Composable
private fun AppDrainCard(app: AppUsageItem, onOpenSettings: () -> Unit) {
    val drainColor = NetraEmerald

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable { onOpenSettings() }
            .padding(14.dp)
            .testTag("app_item_${app.packageName}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // App Initial Avatar
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(drainColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = drainColor
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${app.category} • ${app.packageName.take(28)}",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Drain unavailable",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = drainColor
                    )
                    Text(
                        text = "${app.foregroundTimeMinutes} min foreground",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats Sub-Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Foreground: ${app.foregroundTimeMinutes}m",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Energy unavailable",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "Manage", tint = NetraCyan, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AppKpiBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
