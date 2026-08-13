package com.ventus.sys.ui.scorer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

private const val AXIS_COUNT = 7
private const val GRID_RING_OUTER = 1.0f
private const val GRID_RING_MID = 0.6f
private const val GRID_RING_INNER = 0.3f
private val AXIS_LABELS = listOf("NRG", "VAL", "DNC", "LOUD", "BPM", "ACST", "INST")

/**
 * Ports app.js's scorer radar (buildScorerRadar, app.js:250-279) exactly:
 * same 7-axis order (NRG/VAL/DNC/LOUD/BPM/ACST/INST), same "start at top,
 * clockwise" angle convention, two overlaid polygons (taste profile vs.
 * the current track). [profileValues]/[trackValues] are each 7 floats
 * already normalized 0-1 — see LiveScorerScreen's toRadarValues() for the
 * exact per-axis normalization (matches ScoreEngine's own, e.g. loudness
 * via the same (db+30)/30 curve).
 */
@Composable
fun RadarChart(
    profileValues: FloatArray,
    trackValues: FloatArray?,
    modifier: Modifier = Modifier,
) {
    val profileColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 * 0.75f

            listOf(GRID_RING_OUTER, GRID_RING_MID, GRID_RING_INNER).forEach { ringScale ->
                drawPath(
                    path = ringPath(center, radius * ringScale),
                    color = gridColor,
                    style = Stroke(width = 1f),
                )
            }

            for (i in 0 until AXIS_COUNT) {
                val point = axisPoint(center, radius, i, 1f)
                drawLine(color = gridColor, start = center, end = point, strokeWidth = 1f)
            }

            drawPath(
                path = polygonPath(center, radius, profileValues),
                color = profileColor.copy(alpha = 0.18f),
            )
            drawPath(
                path = polygonPath(center, radius, profileValues),
                color = profileColor,
                style = Stroke(width = 3f),
            )

            if (trackValues != null) {
                drawPath(
                    path = polygonPath(center, radius, trackValues),
                    color = trackColor.copy(alpha = 0.18f),
                )
                drawPath(
                    path = polygonPath(center, radius, trackValues),
                    color = trackColor,
                    style = Stroke(width = 3f),
                )
            }

            drawIntoCanvas { canvas ->
                val paint =
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb()
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = size.minDimension * 0.045f
                    }
                AXIS_LABELS.forEachIndexed { i, label ->
                    val labelPoint = axisPoint(center, radius * 1.18f, i, 1f)
                    canvas.nativeCanvas.drawText(label, labelPoint.x, labelPoint.y, paint)
                }
            }
        }
    }
}

private fun axisAngle(index: Int): Double = (2 * Math.PI * index / AXIS_COUNT) - Math.PI / 2

private fun axisPoint(
    center: Offset,
    radius: Float,
    index: Int,
    value: Float,
): Offset {
    val angle = axisAngle(index)
    return Offset(
        x = center.x + radius * value * cos(angle).toFloat(),
        y = center.y + radius * value * sin(angle).toFloat(),
    )
}

private fun ringPath(
    center: Offset,
    radius: Float,
): Path =
    Path().apply {
        for (i in 0 until AXIS_COUNT) {
            val point = axisPoint(center, radius, i, 1f)
            if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }

private fun polygonPath(
    center: Offset,
    radius: Float,
    values: FloatArray,
): Path =
    Path().apply {
        for (i in 0 until AXIS_COUNT) {
            val point = axisPoint(center, radius, i, values.getOrElse(i) { 0f }.coerceIn(0f, 1f))
            if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
