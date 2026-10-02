package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LiveChargingSample
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import java.util.Locale

enum class LiveGraphMetric {
    POWER,
    VOLTAGE,
    CURRENT,
    PERCENTAGE
}

/**
 * Live session chart rendering separate series on independent scales.
 * Ensures Voltage (V), Current (A), Power (W/mW), and Battery (%) are never conflated.
 */
@Composable
fun LiveChargingGraph(
    samples: List<LiveChargingSample>,
    modifier: Modifier = Modifier,
    emptyText: String = "Awaiting charging telemetry samples (~1/sec)..."
) {
    var requestedMetric by remember { mutableStateOf(LiveGraphMetric.POWER) }

    // Only offer metrics that have at least one real value; a tab with no data is hidden, not shown blank.
    val metricsWithData = LiveGraphMetric.values().filter { m ->
        when (m) {
            LiveGraphMetric.POWER -> samples.any { it.powerWatts != null }
            LiveGraphMetric.VOLTAGE -> samples.any { it.voltageV != null }
            LiveGraphMetric.CURRENT -> samples.any { it.currentA != null }
            LiveGraphMetric.PERCENTAGE -> samples.any { it.batteryPercent != null }
        }
    }
    val selectedMetric = if (metricsWithData.isEmpty() || requestedMetric in metricsWithData) {
        requestedMetric
    } else {
        metricsWithData.first()
    }

    val primaryColor = when (selectedMetric) {
        LiveGraphMetric.POWER -> NetraEmerald
        LiveGraphMetric.VOLTAGE -> NetraCyan
        LiveGraphMetric.CURRENT -> StatusAmber
        LiveGraphMetric.PERCENTAGE -> Color(0xFF64B5F6)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Metric Switcher Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                LiveGraphMetric.POWER to "Power",
                LiveGraphMetric.VOLTAGE to "Voltage",
                LiveGraphMetric.CURRENT to "Current",
                LiveGraphMetric.PERCENTAGE to "Level"
            ).filter { (metric, _) -> metricsWithData.isEmpty() || metric in metricsWithData }.forEach { (metric, label) ->
                val isSelected = selectedMetric == metric
                FilterChip(
                    selected = isSelected,
                    onClick = { requestedMetric = metric },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor.copy(alpha = 0.25f),
                        selectedLabelColor = primaryColor
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Extract series values
        val values: List<Float> = when (selectedMetric) {
            LiveGraphMetric.POWER -> samples.mapNotNull { it.powerWatts }
            LiveGraphMetric.VOLTAGE -> samples.mapNotNull { it.voltageV }
            LiveGraphMetric.CURRENT -> samples.mapNotNull { it.currentA }
            LiveGraphMetric.PERCENTAGE -> samples.mapNotNull { it.batteryPercent }
        }

        if (values.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val minVal = values.minOrNull() ?: 0f
            val maxVal = values.maxOrNull() ?: 1f
            val range = if (maxVal - minVal > 0.001f) maxVal - minVal else 1f
            val displayMax = maxVal + (range * 0.1f)
            val displayMin = (minVal - (range * 0.1f)).coerceAtLeast(0f)
            val effectiveRange = if (displayMax - displayMin > 0.001f) displayMax - displayMin else 1f

            val unitLabel = when (selectedMetric) {
                LiveGraphMetric.POWER -> if (maxVal < 1.0f) "mW" else "W"
                LiveGraphMetric.VOLTAGE -> "V"
                LiveGraphMetric.CURRENT -> "A"
                LiveGraphMetric.PERCENTAGE -> "%"
            }

            val formatValue: (Float) -> String = { v ->
                when (selectedMetric) {
                    LiveGraphMetric.POWER -> if (maxVal < 1.0f) "${String.format(Locale.US, "%.1f", v * 1000f)} mW" else "${String.format(Locale.US, "%.2f", v)} W"
                    LiveGraphMetric.VOLTAGE -> "${String.format(Locale.US, "%.3f", v)} V"
                    LiveGraphMetric.CURRENT -> "${String.format(Locale.US, "%.3f", v)} A"
                    LiveGraphMetric.PERCENTAGE -> if (v % 1.0f == 0.0f) "${v.toInt()}%" else String.format(Locale.US, "%.2f%%", v)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                // Header with current reading & min/max scale
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scale: ${formatValue(displayMin)} – ${formatValue(displayMax)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Latest: ${formatValue(values.last())}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas Chart
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("live_charging_graph_canvas")
                ) {
                    val w = size.width
                    val h = size.height

                    // Gridlines
                    val gridColor = Color.White.copy(alpha = 0.08f)
                    drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f))
                    drawLine(gridColor, Offset(0f, h / 2f), Offset(w, h / 2f))
                    drawLine(gridColor, Offset(0f, h), Offset(w, h))

                    val pointsCount = values.size
                    val stepX = if (pointsCount > 1) w / (pointsCount - 1) else w

                    val linePath = Path()
                    val fillPath = Path()

                    values.forEachIndexed { idx, value ->
                        val normalizedY = (value - displayMin) / effectiveRange
                        val x = idx * stepX
                        val y = h - (normalizedY * h)

                        if (idx == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }

                        if (idx == pointsCount - 1) {
                            fillPath.lineTo(x, h)
                            fillPath.close()
                        }
                    }

                    // Draw under-fill gradient
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw line
                    drawPath(
                        path = linePath,
                        color = primaryColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Latest point indicator
                    if (values.isNotEmpty()) {
                        val lastVal = values.last()
                        val lastNormY = (lastVal - displayMin) / effectiveRange
                        val lastX = (values.size - 1) * stepX
                        val lastY = h - (lastNormY * h)

                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 3.dp.toPx(),
                            center = Offset(lastX, lastY)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // X-Axis Time indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val sampleCount = values.size
                    val timeSpanSec = sampleCount // ~1 second per sample
                    val spanLabel = if (timeSpanSec >= 60) "-${timeSpanSec / 60}m" else "-${timeSpanSec}s"
                    Text(text = spanLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Rolling Session (Max 300s)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Now", fontSize = 9.sp, color = primaryColor)
                }
            }
        }
    }
}
