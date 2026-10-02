package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BatteryRecord
import com.example.model.DotState
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VicoTrendMetric {
    PERCENTAGE,
    TEMPERATURE,
    COMBINED
}

/**
 * Modern dashboard component visualizing 24-hour battery percentage and temperature trends
 * using the Vico charting library.
 */
@Composable
fun VicoBatteryTrendsDashboard(
    records: List<BatteryRecord>,
    modifier: Modifier = Modifier,
    currentLevel: Int? = null,
    currentTemperature: Float? = null
) {
    var selectedMetric by remember { mutableStateOf(VicoTrendMetric.PERCENTAGE) }

    // Filter to last 24 hours
    val now = System.currentTimeMillis()
    val records24h = remember(records, now) {
        val windowStart = now - 24 * 3600_000L
        records.filter { it.timestamp >= windowStart }.sortedBy { it.timestamp }
    }

    // Downsample if dataset is large to maintain 60fps chart rendering
    val sampledRecords = remember(records24h) {
        if (records24h.size <= 48) {
            records24h
        } else {
            val step = records24h.size / 48.0
            (0 until 48).map { i ->
                records24h[(i * step).toInt().coerceAtMost(records24h.lastIndex)]
            }
        }
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    // Formatter for time on bottom axis
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val bottomTimeLabels = remember(sampledRecords) {
        sampledRecords.map { timeFormat.format(Date(it.timestamp)) }
    }

    val bottomAxisFormatter = remember(bottomTimeLabels) {
        CartesianValueFormatter { _, value, _ ->
            val index = value.toInt()
            if (index in bottomTimeLabels.indices) {
                // Show labels at spaced intervals
                if (index == 0 || index == bottomTimeLabels.lastIndex || index % 10 == 0) {
                    bottomTimeLabels[index]
                } else ""
            } else ""
        }
    }

    val startAxisFormatter = remember(selectedMetric) {
        CartesianValueFormatter { _, value, _ ->
            when (selectedMetric) {
                VicoTrendMetric.PERCENTAGE -> "${value.toInt()}%"
                VicoTrendMetric.TEMPERATURE -> String.format(Locale.US, "%.0f°C", value)
                VicoTrendMetric.COMBINED -> "${value.toInt()}"
            }
        }
    }

    // Line fills and styling
    val emeraldColor = NetraEmerald
    val amberColor = StatusAmber

    val percentageLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(emeraldColor.toArgb())),
        areaFill = LineCartesianLayer.AreaFill.single(Fill(emeraldColor.copy(alpha = 0.15f).toArgb()))
    )

    val temperatureLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(amberColor.toArgb())),
        areaFill = LineCartesianLayer.AreaFill.single(Fill(amberColor.copy(alpha = 0.15f).toArgb()))
    )

    val lineProvider = remember(selectedMetric) {
        when (selectedMetric) {
            VicoTrendMetric.PERCENTAGE -> LineCartesianLayer.LineProvider.series(percentageLine)
            VicoTrendMetric.TEMPERATURE -> LineCartesianLayer.LineProvider.series(temperatureLine)
            VicoTrendMetric.COMBINED -> LineCartesianLayer.LineProvider.series(percentageLine, temperatureLine)
        }
    }

    // Update Vico Chart Model Transaction on dataset or metric change
    LaunchedEffect(sampledRecords, selectedMetric) {
        if (sampledRecords.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    when (selectedMetric) {
                        VicoTrendMetric.PERCENTAGE -> {
                            series(sampledRecords.map { it.level })
                        }
                        VicoTrendMetric.TEMPERATURE -> {
                            series(sampledRecords.map { it.temperature })
                        }
                        VicoTrendMetric.COMBINED -> {
                            series(sampledRecords.map { it.level })
                            series(sampledRecords.map { it.temperature })
                        }
                    }
                }
            }
        }
    }

    // Calculate 24h Summary Statistics
    val levelList = records24h.map { it.level }
    val tempList = records24h.map { it.temperature }

    val minLevel = levelList.minOrNull() ?: currentLevel ?: 0
    val maxLevel = levelList.maxOrNull() ?: currentLevel ?: 0
    val avgLevel = if (levelList.isNotEmpty()) levelList.average().toInt() else currentLevel ?: 0
    val netLevelChange = if (levelList.size >= 2) levelList.last() - levelList.first() else 0

    val minTemp = tempList.minOrNull() ?: currentTemperature ?: 0f
    val maxTemp = tempList.maxOrNull() ?: currentTemperature ?: 0f
    val avgTemp = if (tempList.isNotEmpty()) tempList.average().toFloat() else currentTemperature ?: 0f

    SentinelCard(
        title = "24-Hour Battery & Temperature Trends",
        icon = Icons.AutoMirrored.Filled.ShowChart,
        dotState = if (records24h.isNotEmpty()) DotState.CONNECTED else DotState.STANDBY,
        accentColor = when (selectedMetric) {
            VicoTrendMetric.PERCENTAGE -> NetraEmerald
            VicoTrendMetric.TEMPERATURE -> StatusAmber
            VicoTrendMetric.COMBINED -> NetraCyan
        },
        trailingAction = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NetraCyan.copy(alpha = 0.15f))
                    .border(1.dp, NetraCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "VICO CHARTS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NetraCyan
                )
            }
        },
        modifier = modifier.testTag("vico_battery_trends_dashboard")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Trend Metric Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedMetric == VicoTrendMetric.PERCENTAGE,
                    onClick = { selectedMetric = VicoTrendMetric.PERCENTAGE },
                    label = { Text("🔋 Level (%)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NetraEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = NetraEmerald
                    ),
                    modifier = Modifier.weight(1f).testTag("trend_chip_percentage")
                )
                FilterChip(
                    selected = selectedMetric == VicoTrendMetric.TEMPERATURE,
                    onClick = { selectedMetric = VicoTrendMetric.TEMPERATURE },
                    label = { Text("🌡️ Temp (°C)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusAmber.copy(alpha = 0.2f),
                        selectedLabelColor = StatusAmber
                    ),
                    modifier = Modifier.weight(1f).testTag("trend_chip_temperature")
                )
                FilterChip(
                    selected = selectedMetric == VicoTrendMetric.COMBINED,
                    onClick = { selectedMetric = VicoTrendMetric.COMBINED },
                    label = { Text("⚡ Dual View", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NetraCyan.copy(alpha = 0.2f),
                        selectedLabelColor = NetraCyan
                    ),
                    modifier = Modifier.weight(1f).testTag("trend_chip_combined")
                )
            }

            if (sampledRecords.size >= 2) {
                // Interactive Vico Cartesian Chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetraSurface.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("vico_chart_host")
                ) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(lineProvider = lineProvider),
                            startAxis = VerticalAxis.rememberStart(valueFormatter = startAxisFormatter),
                            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter)
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Legend for Combined View
                if (selectedMetric == VicoTrendMetric.COMBINED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(NetraEmerald, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Battery Level (%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(modifier = Modifier.size(10.dp).background(StatusAmber, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Temperature (°C)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                // Informative State when few records have been collected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetraSurface.copy(alpha = 0.4f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NetraCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Recording 24-Hour Telemetry",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (records24h.isEmpty()) {
                                    "Background sentinel is active. Telemetry data points will populate the Vico trend chart as samples are recorded."
                                } else {
                                    "1 sample collected (${records24h.first().level}%, ${String.format(Locale.US, "%.1f°C", records24h.first().temperature)}). Additional background samples will connect the 24h trendline."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Summary Statistics KPI Cards
            Text(
                text = "24-Hour Trend Analytics (${records24h.size} samples)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().testTag("vico_stats_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Battery Level Stats Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetraEmerald.copy(alpha = 0.08f))
                        .border(1.dp, NetraEmerald.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = NetraEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LEVEL STATS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NetraEmerald)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Range:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$minLevel% - $maxLevel%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Average:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$avgLevel%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net Delta:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${if (netLevelChange >= 0) "+" else ""}$netLevelChange%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (netLevelChange >= 0) NetraEmerald else StatusAmber
                            )
                        }
                    }
                }

                // Temperature Stats Card
                val isHot = maxTemp >= 40.0f
                val tempAccent = if (isHot) StatusRed else StatusAmber

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tempAccent.copy(alpha = 0.08f))
                        .border(1.dp, tempAccent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeviceThermostat, contentDescription = null, tint = tempAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("THERMAL STATS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tempAccent)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Peak Temp:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "%.1f°C", maxTemp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isHot) StatusRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Low Temp:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "%.1f°C", minTemp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Average:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "%.1f°C", avgTemp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
