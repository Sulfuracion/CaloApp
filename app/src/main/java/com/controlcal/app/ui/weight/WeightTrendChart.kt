package com.controlcal.app.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.controlcal.app.R
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.tdee.WeightTrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun WeightTrendChart(
    points: List<WeightTrendPoint>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.weight_chart_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.weight_chart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (points.size == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.weight_chart_insufficient),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ChartCanvasWithScrubber(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                ChartLegend()
            }
        }
    }
}

@Composable
private fun ChartLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Raw weigh-in marker
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.weight_chart_legend_weigh_in),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(24.dp))

        // Trend line marker
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(3.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(1.5.dp)
                )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.weight_chart_legend_trend),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartCanvasWithScrubber(
    points: List<WeightTrendPoint>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = onSurfaceColor.copy(alpha = 0.6f),
    )
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold,
        color = onPrimaryContainerColor,
    )

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))
    }
    val parsedDates = remember(points) {
        points.map { LocalDate.parse(it.date) }
    }

    val latestPoint = points.lastOrNull()
    val earliestPoint = points.firstOrNull()
    val changeDirection = if (latestPoint != null && earliestPoint != null) {
        val netChange = latestPoint.trendKg - earliestPoint.trendKg
        val changeFormatted = NumberFormatHelper.formatDecimal(abs(netChange))
        when {
            netChange > 0.05 -> stringResource(R.string.weight_chart_change_increase, changeFormatted)
            netChange < -0.05 -> stringResource(R.string.weight_chart_change_decrease, changeFormatted)
            else -> stringResource(R.string.weight_chart_change_stable)
        }
    } else {
        stringResource(R.string.weight_chart_change_stable)
    }
    val chartDescription = if (latestPoint != null && earliestPoint != null) {
        stringResource(
            R.string.weight_chart_accessibility_summary,
            points.size,
            NumberFormatHelper.formatDecimal(latestPoint.weightKg),
            NumberFormatHelper.formatDecimal(latestPoint.trendKg),
            changeDirection,
        )
    } else {
        stringResource(R.string.weight_chart_accessibility_empty)
    }
    val pointActions = points.mapIndexed { index, _ ->
        CustomAccessibilityAction(
            label = stringResource(
                R.string.weight_chart_select_point,
                index + 1,
                parsedDates[index].format(dateFormatter),
            ),
            action = {
                selectedPointIndex = index
                true
            },
        )
    }
    val selectedTooltipText = selectedPointIndex?.let { index ->
        points.getOrNull(index)?.let { point ->
            stringResource(
                R.string.weight_chart_tooltip,
                parsedDates[index].format(dateFormatter),
                NumberFormatHelper.formatDecimal(point.weightKg),
                NumberFormatHelper.formatDecimal(point.trendKg),
            )
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = chartDescription
                    customActions = pointActions
                }
                .pointerInput(points) {
                    val padLeft = 44.dp.toPx()
                    val padRight = 16.dp.toPx()
                    detectTapGestures(
                        onPress = { offset ->
                            val index = findClosestPointIndex(offset.x, size.width.toFloat(), padLeft, padRight, points, parsedDates)
                            selectedPointIndex = index
                            tryAwaitRelease()
                            selectedPointIndex = null
                        }
                    )
                }
                .pointerInput(points) {
                    val padLeft = 44.dp.toPx()
                    val padRight = 16.dp.toPx()
                    detectDragGestures(
                        onDragStart = { offset ->
                            selectedPointIndex = findClosestPointIndex(offset.x, size.width.toFloat(), padLeft, padRight, points, parsedDates)
                        },
                        onDrag = { change, _ ->
                            selectedPointIndex = findClosestPointIndex(change.position.x, size.width.toFloat(), padLeft, padRight, points, parsedDates)
                        },
                        onDragEnd = { selectedPointIndex = null },
                        onDragCancel = { selectedPointIndex = null },
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val paddingLeft = 44.dp.toPx()
            val paddingRight = 16.dp.toPx()
            val paddingTop = 28.dp.toPx()
            val paddingBottom = 28.dp.toPx()

            val chartWidth = canvasWidth - paddingLeft - paddingRight
            val chartHeight = canvasHeight - paddingTop - paddingBottom

            val minVal = points.minOf { min(it.weightKg, it.trendKg) }
            val maxVal = points.maxOf { max(it.weightKg, it.trendKg) }
            val span = (maxVal - minVal).coerceAtLeast(1.0)
            val minY = minVal - (span * 0.15)
            val maxY = maxVal + (span * 0.15)
            val yRange = (maxY - minY).coerceAtLeast(0.1)

            val startEpochDay = parsedDates.first().toEpochDay()
            val endEpochDay = parsedDates.last().toEpochDay()
            val totalDays = (endEpochDay - startEpochDay).coerceAtLeast(1)

            fun xForEpochDay(epochDay: Long): Float {
                val frac = (epochDay - startEpochDay).toFloat() / totalDays.toFloat()
                return paddingLeft + (frac * chartWidth)
            }

            fun yForWeight(kg: Double): Float {
                val frac = ((kg - minY) / yRange).toFloat()
                return (paddingTop + chartHeight) - (frac * chartHeight)
            }

            // 1. Draw horizontal grid lines & Y labels (4 lines)
            val gridSteps = 3
            for (step in 0..gridSteps) {
                val fraction = step.toFloat() / gridSteps.toFloat()
                val weightStep = minY + (fraction * yRange)
                val y = paddingTop + chartHeight - (fraction * chartHeight)

                // Grid line
                drawLine(
                    color = outlineVariantColor.copy(alpha = 0.4f),
                    start = Offset(paddingLeft, y),
                    end = Offset(canvasWidth - paddingRight, y),
                    strokeWidth = 1.dp.toPx(),
                )

                // Label
                val labelText = NumberFormatHelper.formatDecimal(weightStep)
                val measured = textMeasurer.measure(labelText, style = axisLabelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = labelText,
                    topLeft = Offset(paddingLeft - measured.size.width - 4.dp.toPx(), y - (measured.size.height / 2)),
                    style = axisLabelStyle,
                )
            }

            // 2. Draw trend gradient fill & line (EMA)
            val trendPath = Path()
            val trendFillPath = Path()

            val firstX = xForEpochDay(startEpochDay)
            val firstTrendY = yForWeight(points.first().trendKg)
            trendPath.moveTo(firstX, firstTrendY)
            trendFillPath.moveTo(firstX, paddingTop + chartHeight)
            trendFillPath.lineTo(firstX, firstTrendY)

            for (i in 1 until points.size) {
                val pt = points[i]
                val epochDay = parsedDates[i].toEpochDay()
                val x = xForEpochDay(epochDay)
                val y = yForWeight(pt.trendKg)
                trendPath.lineTo(x, y)
                trendFillPath.lineTo(x, y)
            }

            val lastPt = points.last()
            val lastEpochDay = parsedDates.last().toEpochDay()
            val lastX = xForEpochDay(lastEpochDay)
            trendFillPath.lineTo(lastX, paddingTop + chartHeight)
            trendFillPath.close()

            // Draw area under trend
            drawPath(
                path = trendFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.22f),
                        primaryColor.copy(alpha = 0.02f),
                    ),
                    startY = paddingTop,
                    endY = paddingTop + chartHeight,
                )
            )

            // Draw trend line
            drawPath(
                path = trendPath,
                color = primaryColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
            )

            // 3. Draw weigh-in connecting lines & points
            for (i in 0 until points.size - 1) {
                val current = points[i]
                val next = points[i + 1]
                val x1 = xForEpochDay(parsedDates[i].toEpochDay())
                val y1 = yForWeight(current.weightKg)
                val x2 = xForEpochDay(parsedDates[i + 1].toEpochDay())
                val y2 = yForWeight(next.weightKg)

                drawLine(
                    color = tertiaryColor.copy(alpha = 0.35f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            for (i in points.indices) {
                val pt = points[i]
                val x = xForEpochDay(parsedDates[i].toEpochDay())
                val y = yForWeight(pt.weightKg)

                // Outer border circle
                drawCircle(
                    color = surfaceColor,
                    radius = 4.5.dp.toPx(),
                    center = Offset(x, y),
                )
                // Inner filled circle
                drawCircle(
                    color = tertiaryColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(x, y),
                )
            }

            // 4. Draw X-axis date labels (first, middle, last)
            val dateIndices = if (points.size <= 2) {
                listOf(0, points.size - 1)
            } else {
                listOf(0, points.size / 2, points.size - 1)
            }

            for (idx in dateIndices) {
                val pt = points[idx]
                val parsed = parsedDates[idx]
                val text = parsed.format(dateFormatter)
                val measured = textMeasurer.measure(text, style = axisLabelStyle)
                val x = xForEpochDay(parsed.toEpochDay()) - (measured.size.width / 2)
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    topLeft = Offset(x.coerceIn(paddingLeft, canvasWidth - paddingRight - measured.size.width), canvasHeight - paddingBottom + 6.dp.toPx()),
                    style = axisLabelStyle,
                )
            }

            // 5. Draw scrubber highlight and tooltip if active
            selectedPointIndex?.let { idx ->
                val selectedPoint = points.getOrNull(idx) ?: return@let
                val parsedDate = parsedDates[idx]
                val x = xForEpochDay(parsedDate.toEpochDay())
                val yRaw = yForWeight(selectedPoint.weightKg)
                val yTrend = yForWeight(selectedPoint.trendKg)

                // Vertical indicator dashed line
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.4f),
                    start = Offset(x, paddingTop),
                    end = Offset(x, paddingTop + chartHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                )

                // Highlight raw weight point
                drawCircle(
                    color = tertiaryColor,
                    radius = 6.dp.toPx(),
                    center = Offset(x, yRaw),
                )
                drawCircle(
                    color = surfaceColor,
                    radius = 2.dp.toPx(),
                    center = Offset(x, yRaw),
                )

                // Highlight trend point
                drawCircle(
                    color = primaryColor,
                    radius = 6.dp.toPx(),
                    center = Offset(x, yTrend),
                )
                drawCircle(
                    color = surfaceColor,
                    radius = 2.dp.toPx(),
                    center = Offset(x, yTrend),
                )

                // Scrubber tooltip box
                val tooltipText = selectedTooltipText ?: return@let

                val tooltipMeasured = textMeasurer.measure(tooltipText, style = tooltipStyle)
                val tooltipWidth = tooltipMeasured.size.width + 16.dp.toPx()
                val tooltipHeight = tooltipMeasured.size.height + 8.dp.toPx()

                var tooltipX = x - (tooltipWidth / 2)
                if (tooltipX < paddingLeft) tooltipX = paddingLeft
                if (tooltipX + tooltipWidth > canvasWidth - paddingRight) {
                    tooltipX = canvasWidth - paddingRight - tooltipWidth
                }
                val tooltipY = (paddingTop - tooltipHeight - 4.dp.toPx()).coerceAtLeast(0f)

                drawRoundRect(
                    color = primaryContainerColor,
                    topLeft = Offset(tooltipX, tooltipY),
                    size = Size(tooltipWidth, tooltipHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = tooltipText,
                    topLeft = Offset(tooltipX + 8.dp.toPx(), tooltipY + 4.dp.toPx()),
                    style = tooltipStyle,
                )
            }
        }
    }
}

private fun findClosestPointIndex(
    touchX: Float,
    canvasWidth: Float,
    paddingLeft: Float,
    paddingRight: Float,
    points: List<WeightTrendPoint>,
    parsedDates: List<LocalDate>,
): Int {
    if (points.isEmpty()) return 0
    val startDay = parsedDates.first().toEpochDay()
    val endDay = parsedDates.last().toEpochDay()
    val totalDays = (endDay - startDay).coerceAtLeast(1)

    val chartWidth = (canvasWidth - paddingLeft - paddingRight).coerceAtLeast(1f)

    var closestIndex = 0
    var minDiff = Float.MAX_VALUE

    for (i in points.indices) {
        val day = parsedDates[i].toEpochDay()
        val x = paddingLeft + ((day - startDay).toFloat() / totalDays.toFloat()) * chartWidth
        val diff = abs(touchX - x)
        if (diff < minDiff) {
            minDiff = diff
            closestIndex = i
        }
    }
    return closestIndex
}
