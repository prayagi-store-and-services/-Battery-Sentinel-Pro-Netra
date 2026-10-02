package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import kotlin.math.sin

/**
 * Large animated battery graphic that visually fills upward as actual battery percentage increases.
 * Continues charging animation strictly during a confirmed charging session.
 * Displays neutral stationary graphic when discharging.
 */
@Composable
fun LiveBatteryGraphic(
    batteryPercent: Float?,
    isCharging: Boolean,
    pluggedSource: String?,
    modifier: Modifier = Modifier
) {
    val validPercent = (batteryPercent ?: 0f).coerceIn(0f, 100f)
    val fillTarget = validPercent / 100f

    // Smoothly interpolate fill height toward latest measured percentage
    val animatedFillRatio by animateFloatAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "battery_fill_animation"
    )

    // Infinite wave pulse runs strictly while actively charging
    val infiniteTransition = rememberInfiniteTransition(label = "charging_pulse")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isCharging) 2f * Math.PI.toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_offset"
    )

    val boltAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (isCharging) 1.0f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bolt_alpha"
    )

    val fillColor = when {
        validPercent <= 15f -> StatusRed
        validPercent <= 30f -> StatusAmber
        isCharging -> NetraEmerald
        else -> NetraCyan
    }

    val shellColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Battery Canvas Container
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(130.dp, 200.dp)
                    .testTag("live_battery_canvas")
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Terminal Cap (+) at top
                val capWidth = canvasWidth * 0.35f
                val capHeight = 10.dp.toPx()
                val capX = (canvasWidth - capWidth) / 2f
                val capY = 0f
                drawRoundRect(
                    color = shellColor,
                    topLeft = Offset(capX, capY),
                    size = Size(capWidth, capHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Battery Outer Shell
                val bodyY = capHeight + 2.dp.toPx()
                val bodyHeight = canvasHeight - bodyY
                val bodyWidth = canvasWidth
                val strokeWidth = 3.5.dp.toPx()
                val cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

                // Background inside shell
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(0f, bodyY),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = cornerRadius
                )

                // Outer Shell Stroke
                drawRoundRect(
                    color = if (isCharging) NetraCyan.copy(alpha = 0.8f) else shellColor,
                    topLeft = Offset(0f, bodyY),
                    size = Size(bodyWidth, bodyHeight),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = strokeWidth)
                )

                // Liquid Fill calculation (fills upward from bottom)
                val innerPadding = 6.dp.toPx()
                val innerWidth = bodyWidth - (innerPadding * 2)
                val maxInnerHeight = bodyHeight - (innerPadding * 2)
                val filledHeight = maxInnerHeight * animatedFillRatio

                if (filledHeight > 0) {
                    val fillTopY = (bodyY + bodyHeight - innerPadding) - filledHeight
                    val fillLeftX = innerPadding
                    val fillBottomY = bodyY + bodyHeight - innerPadding

                    val fillBrush = Brush.verticalGradient(
                        colors = listOf(
                            if (isCharging) NetraCyan else fillColor.copy(alpha = 0.9f),
                            fillColor
                        ),
                        startY = fillTopY,
                        endY = fillBottomY
                    )

                    val path = Path()
                    path.moveTo(fillLeftX, fillBottomY)
                    path.lineTo(fillLeftX + innerWidth, fillBottomY)

                    // Draw wave crest at the top of the liquid fill if charging
                    if (isCharging && animatedFillRatio > 0.05f && animatedFillRatio < 0.98f) {
                        path.lineTo(fillLeftX + innerWidth, fillTopY)
                        val steps = 30
                        val stepWidth = innerWidth / steps
                        for (i in steps downTo 0) {
                            val x = fillLeftX + (i * stepWidth)
                            val waveHeight = 3.dp.toPx() * sin((i.toFloat() / steps * 4f * Math.PI.toFloat()) + waveOffset)
                            val y = fillTopY + waveHeight
                            path.lineTo(x, y)
                        }
                    } else {
                        path.lineTo(fillLeftX + innerWidth, fillTopY)
                        path.lineTo(fillLeftX, fillTopY)
                    }
                    path.close()

                    drawPath(
                        path = path,
                        brush = fillBrush
                    )
                }
            }

            // Center Bolt Icon overlay when charging
            if (isCharging) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Actively Charging",
                    tint = Color.White.copy(alpha = boltAlpha),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("charging_bolt_icon")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large prominent percentage reading
        val percentText = when {
            batteryPercent == null -> "Unavailable"
            batteryPercent % 1.0f == 0.0f -> "${batteryPercent.toInt()}%"
            else -> String.format(java.util.Locale.US, "%.2f%%", batteryPercent)
        }
        Text(
            text = percentText,
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("live_battery_percentage_text")
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Charging / Discharging badge
        val statusText = when {
            isCharging -> "⚡ CHARGING (${pluggedSource ?: "AC"})"
            pluggedSource == "IDLE" -> "🔌 CHARGER CONNECTED (FULL / IDLE)"
            else -> "🔋 DISCHARGING"
        }
        val badgeColor = when {
            isCharging -> NetraEmerald
            pluggedSource == "IDLE" -> NetraCyan
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Text(
            text = statusText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            modifier = Modifier.testTag("charging_status_badge")
        )
    }
}
