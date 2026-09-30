package com.example.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.example.data.local.BatteryRecord

/**
 * High-performance, anti-aliased Canvas renderer generating sparkline & dual-series
 * graphs for RemoteViews in Android AppWidgets.
 */
object WidgetGraphRenderer {

    enum class GraphMetric {
        CURRENT,
        POWER,
        TEMPERATURE,
        CURRENT_AND_VOLTAGE,
        WATTAGE_AND_VOLTAGE
    }

    /**
     * Generates a smooth, anti-aliased bitmap for single or dual series historical telemetry.
     */
    fun renderGraphBitmap(
        records: List<BatteryRecord>,
        metric: GraphMetric,
        width: Int = 600,
        height: Int = 240,
        primaryColorHex: String = "#00E5FF",
        secondaryColorHex: String = "#FFD700"
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0C1420")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Grid lines
        val gridPaint = Paint().apply {
            color = Color.parseColor("#1E2C3D")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
            isAntiAlias = true
        }

        val paddingH = 20f
        val paddingTop = 24f
        val paddingBottom = 24f
        val usableWidth = width - (paddingH * 2)
        val usableHeight = height - paddingTop - paddingBottom

        // Draw horizontal grid lines (top, middle, bottom)
        canvas.drawLine(paddingH, paddingTop, width - paddingH, paddingTop, gridPaint)
        canvas.drawLine(paddingH, paddingTop + usableHeight / 2f, width - paddingH, paddingTop + usableHeight / 2f, gridPaint)
        canvas.drawLine(paddingH, paddingTop + usableHeight, width - paddingH, paddingTop + usableHeight, gridPaint)

        if (records.size < 2) {
            // Draw placeholder baseline if not enough records
            val emptyPaint = Paint().apply {
                color = Color.parseColor("#455A64")
                textSize = 22f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Awaiting historical telemetry records...", width / 2f, height / 2f + 8f, emptyPaint)
            return bitmap
        }

        val samplePoints = records.takeLast(40)

        when (metric) {
            GraphMetric.CURRENT -> {
                val series = samplePoints.map { Math.abs(it.currentMa).toFloat() }
                drawSingleSeries(canvas, series, width, height, usableWidth, usableHeight, paddingH, paddingTop, primaryColorHex)
            }
            GraphMetric.POWER -> {
                val series = samplePoints.map { it.powerWatts }
                drawSingleSeries(canvas, series, width, height, usableWidth, usableHeight, paddingH, paddingTop, primaryColorHex)
            }
            GraphMetric.TEMPERATURE -> {
                val series = samplePoints.map { it.temperature }
                // Draw 40°C threshold guideline
                drawTemperatureSafetyGuide(canvas, width, height, usableWidth, usableHeight, paddingH, paddingTop, series)
                drawSingleSeries(canvas, series, width, height, usableWidth, usableHeight, paddingH, paddingTop, primaryColorHex)
            }
            GraphMetric.CURRENT_AND_VOLTAGE -> {
                val series1 = samplePoints.map { Math.abs(it.currentMa).toFloat() }
                val series2 = samplePoints.map { it.voltageMv / 1000f }
                drawDualSeries(canvas, series1, series2, usableWidth, usableHeight, paddingH, paddingTop, primaryColorHex, secondaryColorHex)
            }
            GraphMetric.WATTAGE_AND_VOLTAGE -> {
                val series1 = samplePoints.map { it.powerWatts }
                val series2 = samplePoints.map { it.voltageMv / 1000f }
                drawDualSeries(canvas, series1, series2, usableWidth, usableHeight, paddingH, paddingTop, primaryColorHex, secondaryColorHex)
            }
        }

        return bitmap
    }

    private fun drawSingleSeries(
        canvas: Canvas,
        data: List<Float>,
        width: Int,
        height: Int,
        usableWidth: Float,
        usableHeight: Float,
        paddingH: Float,
        paddingTop: Float,
        strokeColorHex: String
    ) {
        val minVal = data.minOrNull() ?: 0f
        val maxVal = data.maxOrNull() ?: 100f
        val range = if (maxVal == minVal) 1f else (maxVal - minVal)

        val strokeColor = Color.parseColor(strokeColorHex)

        val linePaint = Paint().apply {
            color = strokeColor
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            shader = LinearGradient(
                0f, paddingTop, 0f, paddingTop + usableHeight,
                Color.argb(90, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)),
                Color.argb(5, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)),
                Shader.TileMode.CLAMP
            )
        }

        val linePath = Path()
        val fillPath = Path()

        val stepX = usableWidth / (data.size - 1)

        data.forEachIndexed { i, value ->
            val normY = (value - minVal) / range
            val x = paddingH + (i * stepX)
            val y = paddingTop + usableHeight - (normY * usableHeight)

            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, paddingTop + usableHeight)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (i == data.size - 1) {
                fillPath.lineTo(x, paddingTop + usableHeight)
                fillPath.close()
            }
        }

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        // Draw pulsating last point dot
        val lastX = paddingH + ((data.size - 1) * stepX)
        val lastNormY = (data.last() - minVal) / range
        val lastY = paddingTop + usableHeight - (lastNormY * usableHeight)

        val dotGlow = Paint().apply {
            color = strokeColor
            alpha = 100
            isAntiAlias = true
        }
        val dotSolid = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(lastX, lastY, 7f, dotGlow)
        canvas.drawCircle(lastX, lastY, 4f, dotSolid)
    }

    private fun drawDualSeries(
        canvas: Canvas,
        series1: List<Float>,
        series2: List<Float>,
        usableWidth: Float,
        usableHeight: Float,
        paddingH: Float,
        paddingTop: Float,
        color1Hex: String,
        color2Hex: String
    ) {
        val min1 = series1.minOrNull() ?: 0f
        val max1 = series1.maxOrNull() ?: 100f
        val range1 = if (max1 == min1) 1f else (max1 - min1)

        val min2 = series2.minOrNull() ?: 0f
        val max2 = series2.maxOrNull() ?: 100f
        val range2 = if (max2 == min2) 1f else (max2 - min2)

        val stepX = usableWidth / (series1.size - 1)

        // Series 1
        val paint1 = Paint().apply {
            color = Color.parseColor(color1Hex)
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        val path1 = Path()
        series1.forEachIndexed { i, val1 ->
            val normY = (val1 - min1) / range1
            val x = paddingH + (i * stepX)
            val y = paddingTop + usableHeight - (normY * usableHeight)
            if (i == 0) path1.moveTo(x, y) else path1.lineTo(x, y)
        }
        canvas.drawPath(path1, paint1)

        // Series 2
        val paint2 = Paint().apply {
            color = Color.parseColor(color2Hex)
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        val path2 = Path()
        series2.forEachIndexed { i, val2 ->
            val normY = (val2 - min2) / range2
            val x = paddingH + (i * stepX)
            val y = paddingTop + usableHeight - (normY * usableHeight)
            if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
        }
        canvas.drawPath(path2, paint2)
    }

    private fun drawTemperatureSafetyGuide(
        canvas: Canvas,
        width: Int,
        height: Int,
        usableWidth: Float,
        usableHeight: Float,
        paddingH: Float,
        paddingTop: Float,
        data: List<Float>
    ) {
        val minVal = Math.min(25f, data.minOrNull() ?: 25f)
        val maxVal = Math.max(48f, data.maxOrNull() ?: 48f)
        val range = maxVal - minVal

        val guideY = paddingTop + usableHeight - (((40.0f - minVal) / range) * usableHeight)

        if (guideY in paddingTop..(paddingTop + usableHeight)) {
            val redGuidePaint = Paint().apply {
                color = Color.parseColor("#FF5252")
                strokeWidth = 2f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                isAntiAlias = true
            }
            canvas.drawLine(paddingH, guideY, width - paddingH, guideY, redGuidePaint)

            val textPaint = Paint().apply {
                color = Color.parseColor("#FF8A80")
                textSize = 16f
                isAntiAlias = true
            }
            canvas.drawText("40°C Safety Limit", width - paddingH - 120f, guideY - 4f, textPaint)
        }
    }
}
