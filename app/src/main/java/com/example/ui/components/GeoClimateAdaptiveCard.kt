package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.model.AdaptiveWarningCategory
import com.example.model.DotState
import com.example.model.LocationPermissionState
import com.example.model.NetraCentralState
import com.example.model.TemperatureDeviation
import com.example.model.WeatherCondition
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GeoClimateAdaptiveCard(
    canonical: NetraCentralState,
    viewModel: NetraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val loc = canonical.locationContext
    val weather = canonical.weatherContext
    val adaptive = canonical.adaptiveThermalContext

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refreshEnvironmentalContext(force = true)
        }
    }

    val dotState = when {
        adaptive.warningCategory == AdaptiveWarningCategory.CRITICAL_THERMAL -> DotState.CRITICAL
        adaptive.warningCategory == AdaptiveWarningCategory.THERMAL_ANOMALY -> DotState.THROTTLED
        adaptive.warningCategory == AdaptiveWarningCategory.CLIMATE_ELEVATED -> DotState.STANDBY
        weather.isAvailable -> DotState.CONNECTED
        else -> DotState.STANDBY
    }

    val accentColor = when (adaptive.warningCategory) {
        AdaptiveWarningCategory.CRITICAL_THERMAL -> DangerRed
        AdaptiveWarningCategory.THERMAL_ANOMALY -> StatusAmber
        AdaptiveWarningCategory.CLIMATE_ELEVATED -> StatusAmber
        AdaptiveWarningCategory.NORMAL_IDLE -> NetraEmerald
        AdaptiveWarningCategory.INSUFFICIENT_CONTEXT -> NetraCyan
    }

    SentinelCard(
        title = "Geo-Climate & Thermal Baseline",
        icon = Icons.Default.Public,
        dotState = dotState,
        accentColor = accentColor,
        trailingAction = {
            IconButton(
                onClick = {
                    isRefreshing = true
                    viewModel.refreshEnvironmentalContext(force = true)
                    coroutineScope.launch {
                        delay(1200)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.size(28.dp).testTag("refresh_climate_button")
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NetraCyan, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Climate", tint = NetraCyan, modifier = Modifier.size(18.dp))
                }
            }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // 1. Location Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = NetraCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val locationDisplay = buildString {
                        if (!loc.locality.isNullOrBlank()) append("${loc.locality}, ")
                        if (!loc.regionName.isNullOrBlank()) append("${loc.regionName}, ")
                        append(loc.countryName ?: "Unknown Location")
                    }
                    Text(
                        text = locationDisplay,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                loc.countryCode?.let { code ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NetraCyan.copy(alpha = 0.15f))
                            .border(0.5.dp, NetraCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = code, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = NetraCyan)
                    }
                }
            }

            // 2. Weather & Ambient Context Grid
            if (weather.isAvailable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (weather.condition) {
                                        WeatherCondition.CLEAR -> Icons.Default.WbSunny
                                        WeatherCondition.RAIN, WeatherCondition.STORM -> Icons.Default.WaterDrop
                                        WeatherCondition.EXTREME_HEAT -> Icons.Default.Warning
                                        else -> Icons.Default.DeviceThermostat
                                    },
                                    contentDescription = null,
                                    tint = if (weather.condition == WeatherCondition.EXTREME_HEAT) DangerRed else NetraCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = weather.conditionText ?: "Ambient Weather",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            weather.temperatureCelsius?.let { temp ->
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", temp)}°C Ambient",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (temp >= 38f) StatusAmber else NetraCyan
                                )
                            }
                        }

                        // Weather Sub-metrics (Feels like, Humidity, Wind)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weather.feelsLikeCelsius?.let { feels ->
                                Text(
                                    text = "Feels like: ${String.format(java.util.Locale.US, "%.1f", feels)}°C",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            weather.humidityPercent?.let { hum ->
                                Text(
                                    text = "Humidity: $hum%",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            weather.windSpeedKmh?.let { wind ->
                                Text(
                                    text = "Wind: ${String.format(java.util.Locale.US, "%.1f", wind)} km/h",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Location / Weather acquisition prompt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Grant coarse location to enable weather-aware adaptive baseline",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    ) {
                        Text("Enable Weather", fontSize = 11.sp)
                    }
                }
            }

            // Severe weather banner if active
            weather.severeWeatherAlert?.let { alert ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusAmber.copy(alpha = 0.2f))
                        .border(1.dp, StatusAmber.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = alert, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAmber)
                    }
                }
            }

            // 3. Adaptive Climate Baseline Evaluation Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        accentColor.copy(alpha = 0.35f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Climate Profile: ${adaptive.climateProfileLabel}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (adaptive.expectedIdleBatteryMin != null && adaptive.expectedIdleBatteryMax != null) {
                                Text(
                                    text = "Expected Idle Battery: ${adaptive.expectedIdleBatteryMin.toInt()}°C – ${adaptive.expectedIdleBatteryMax.toInt()}°C",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val statusBadgeColor = when (adaptive.temperatureDeviation) {
                            TemperatureDeviation.WITHIN_BASELINE -> StatusGreen
                            TemperatureDeviation.BELOW_BASELINE -> NetraCyan
                            TemperatureDeviation.ABOVE_BASELINE -> StatusAmber
                            TemperatureDeviation.STRONGLY_ABOVE_BASELINE -> StatusRed
                            TemperatureDeviation.INSUFFICIENT_CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusBadgeColor.copy(alpha = 0.15f))
                                .border(0.5.dp, statusBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = adaptive.temperatureDeviation.name.replace('_', ' '),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusBadgeColor
                            )
                        }
                    }

                    // Diagnostic Message Narrative
                    Text(
                        text = adaptive.diagnosticMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )

                    // Environmental Attribution Tag
                    if (adaptive.isHotWeatherAttributed) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusAmber.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✓ Weather-Attributed Normal: Elevated temperature is due to ambient climate, not background app malfunction.",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = StatusAmber
                            )
                        }
                    }
                }
            }

            // 4. Footer info: Data Source & Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Source: ${weather.weatherSource ?: loc.locationSource ?: "Locale"}",
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = "Confidence: ${(adaptive.environmentalConfidence * 100).toInt()}%",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NetraCyan
                )
            }
        }
    }
}
