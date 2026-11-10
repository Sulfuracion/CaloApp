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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.controlcal.app.R
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.tdee.WeightTrendPoint
import com.controlcal.core.weight.WeightPlotPoint
import com.controlcal.core.weight.WeightRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun WeightRange.toNameStringRes(): Int {
    return when (this) {
        WeightRange.LAST_7_DAYS -> R.string.weight_range_name_7d
        WeightRange.LAST_15_DAYS -> R.string.weight_range_name_15d
        WeightRange.LAST_1_MONTH -> R.string.weight_range_name_1m
        WeightRange.LAST_6_MONTHS -> R.string.weight_range_name_6m
        WeightRange.LAST_1_YEAR -> R.string.weight_range_name_1a
        WeightRange.ALL -> R.string.weight_range_name_all
    }
}

@Composable
fun WeightTrendChart(
    trendPoints: List<WeightTrendPoint>,
    plotPoints: List<WeightPlotPoint>,
    selectedRange: WeightRange = WeightRange.LAST_1_MONTH,
    isWeeklyAggregated: Boolean = false,
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

            if (plotPoints.isEmpty()) {
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
            } else if (plotPoints.size < 2 && trendPoints.size < 2) {
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
                    trendPoints = trendPoints,
                    plotPoints = plotPoints,
                    selectedRange = selectedRange,
                    isWeeklyAggregated = isWeeklyAggregated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                ChartLegend(isWeeklyAggregated = isWeeklyAggregated)
            }
        }
    }
}

@Composable
fun WeightTrendChart(
    points: List<WeightTrendPoint>,
    modifier: Modifier = Modifier,
) {
    val plotPoints = remember(points) {
        points.map {
            WeightPlotPoint(
                date = it.date,
                weightKg = it.weightKg,
                trendKg = it.trendKg,
                isWeeklyAverage = false,
                sampleCount = 1,
            )
        }
    }
    WeightTrendChart(
        trendPoints = points,
        plotPoints = plotPoints,
        selectedRange = WeightRange.LAST_1_MONTH,
        isWeeklyAggregated = false,
        modifier = modifier,
    )
}

@Composable
private fun ChartLegend(isWeeklyAggregated: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Raw weigh-in or weekly average marker
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isWeeklyAggregated) {
                stringResource(R.string.weight_chart_legend_weekly_average)
            } else {
                stringResource(R.string.weight_chart_legend_weigh_in)
            },
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
    trendPoints: List<WeightTrendPoint>,
    plotPoints: List<WeightPlotPoint>,
    selectedRange: WeightRange,
    isWeeklyAggregated: Boolean,
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

    val esLocale = remember { Locale.forLanguageTag("es-ES") }
    val dayMonthFormatter = remember { DateTimeFormatter.ofPattern("d MMM", esLocale) }
    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMM yy", esLocale) }
    val monthOnlyFormatter = remember { DateTimeFormatter.ofPattern("MMM", esLocale) }

    val parsedPlotDates = remember(plotPoints) {
        plotPoints.map { LocalDate.parse(it.date) }
    }
    val parsedTrendDates = remember(trendPoints) {
        trendPoints.map { LocalDate.parse(it.date) }
    }

    val latestTrend = trendPoints.lastOrNull()
    val earliestTrend = trendPoints.firstOrNull()
    val changeDirection = if (latestTrend != null && earliestTrend != null) {
        val netChange = latestTrend.trendKg - earliestTrend.trendKg
        val changeFormatted = NumberFormatHelper.formatDecimal(abs(netChange))
        when {
            netChange > 0.05 -> stringResource(R.string.weight_chart_change_increase, changeFormatted)
            netChange < -0.05 -> stringResource(R.string.weight_chart_change_decrease, changeFormatted)
            else -> stringResource(R.string.weight_chart_change_stable)
        }
    } else {
        stringResource(R.string.weight_chart_change_stable)
    }

    val rangeName = stringResource(selectedRange.toNameStringRes())
    val totalCount = plotPoints.sumOf { it.sampleCount }

    val chartDescription = if (latestTrend != null) {
        val latestWeight = plotPoints.lastOrNull()?.weightKg ?: latestTrend.weightKg
        stringResource(
            R.string.weight_chart_accessibility_summary,
            rangeName,
            totalCount,
            NumberFormatHelper.formatDecimal(latestWeight),
            NumberFormatHelper.formatDecimal(latestTrend.trendKg),
            changeDirection,
        )
    } else {
        stringResource(R.string.weight_chart_accessibility_empty)
    }

    val pointActions = plotPoints.mapIndexed { index, point ->
        val dateLabel = parsedPlotDates[index].format(dayMonthFormatter)
        val actionLabel = if (point.isWeeklyAverage) {
            stringResource(R.string.weight_chart_select_point_weekly, index + 1, dateLabel)
        } else {
            stringResource(R.string.weight_chart_select_point, index + 1, dateLabel)
        }
        CustomAccessibilityAction(
            label = actionLabel,
            action = {
                selectedPointIndex = index
                true
            },
        )
    }

    val selectedTooltipText = selectedPointIndex?.let { index ->
        plotPoints.getOrNull(index)?.let { point ->
            val dateLabel = parsedPlotDates[index].format(dayMonthFormatter)
            val weightStr = NumberFormatHelper.formatDecimal(point.weightKg)
            val trendStr = NumberFormatHelper.formatDecimal(point.trendKg)
            if (point.isWeeklyAverage) {
                stringResource(R.string.weight_chart_tooltip_weekly, dateLabel, weightStr, trendStr)
            } else {
                stringResource(R.string.weight_chart_tooltip, dateLabel, weightStr, trendStr)
            }
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
                .pointerInput(plotPoints) {
                    val padLeft = 44.dp.toPx()
                    val padRight = 16.dp.toPx()
                    detectTapGestures(
                        onPress = { offset ->
                            val index = findClosestPlotPointIndex(offset.x, size.width.toFloat(), padLeft, padRight, plotPoints, parsedPlotDates)
                            selectedPointIndex = index
                            tryAwaitRelease()
                            selectedPointIndex = null
                        }
                    )
                }
                .pointerInput(plotPoints) {
                    val padLeft = 44.dp.toPx()
                    val padRight = 16.dp.toPx()
                    detectDragGestures(
                        onDragStart = { offset ->
                            selectedPointIndex = findClosestPlotPointIndex(offset.x, size.width.toFloat(), padLeft, padRight, plotPoints, parsedPlotDates)
                        },
                        onDrag = { change, _ ->
                            selectedPointIndex = findClosestPlotPointIndex(change.position.x, size.width.toFloat(), padLeft, padRight, plotPoints, parsedPlotDates)
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

            val allWeights = plotPoints.map { it.weightKg } + trendPoints.map { it.trendKg }
            val minVal = allWeights.minOrNull() ?: 70.0
            val maxVal = allWeights.maxOrNull() ?: 80.0
            val span = (maxVal - minVal).coerceAtLeast(1.0)
            val minY = minVal - (span * 0.15)
            val maxY = maxVal + (span * 0.15)
            val yRange = (maxY - minY).coerceAtLeast(0.1)

            val allDates = (parsedPlotDates + parsedTrendDates).sorted()
            if (allDates.isEmpty()) return@Canvas

            val startEpochDay = allDates.first().toEpochDay()
            val endEpochDay = allDates.last().toEpochDay()
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

            // 2. Draw trend gradient fill & line (EMA) using trendPoints
            if (trendPoints.isNotEmpty()) {
                val trendPath = Path()
                val trendFillPath = Path()

                val firstEpochDay = parsedTrendDates.first().toEpochDay()
                val firstX = xForEpochDay(firstEpochDay)
                val firstTrendY = yForWeight(trendPoints.first().trendKg)
                trendPath.moveTo(firstX, firstTrendY)
                trendFillPath.moveTo(firstX, paddingTop + chartHeight)
                trendFillPath.lineTo(firstX, firstTrendY)

                for (i in 1 until trendPoints.size) {
                    val pt = trendPoints[i]
                    val epochDay = parsedTrendDates[i].toEpochDay()
                    val x = xForEpochDay(epochDay)
                    val y = yForWeight(pt.trendKg)
                    trendPath.lineTo(x, y)
                    trendFillPath.lineTo(x, y)
                }

                val lastEpochDay = parsedTrendDates.last().toEpochDay()
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
            }

            // 3. Draw weigh-in connecting lines & points using plotPoints
            if (plotPoints.size > 1) {
                for (i in 0 until plotPoints.size - 1) {
                    val current = plotPoints[i]
                    val next = plotPoints[i + 1]
                    val x1 = xForEpochDay(parsedPlotDates[i].toEpochDay())
                    val y1 = yForWeight(current.weightKg)
                    val x2 = xForEpochDay(parsedPlotDates[i + 1].toEpochDay())
                    val y2 = yForWeight(next.weightKg)

                    drawLine(
                        color = tertiaryColor.copy(alpha = 0.35f),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            for (i in plotPoints.indices) {
                val pt = plotPoints[i]
                val x = xForEpochDay(parsedPlotDates[i].toEpochDay())
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

            // 4. Draw X-axis date labels adapted to the selected range without overlapping
            val isMultiYear = allDates.first().year != allDates.last().year
            val labelFormatter = when (selectedRange) {
                WeightRange.LAST_7_DAYS, WeightRange.LAST_15_DAYS -> dayMonthFormatter
                WeightRange.LAST_1_MONTH -> dayMonthFormatter
                WeightRange.LAST_6_MONTHS, WeightRange.LAST_1_YEAR, WeightRange.ALL -> {
                    if (isMultiYear) monthYearFormatter else monthOnlyFormatter
                }
            }

            // Select candidate dates to label
            val candidateEpochDays = computeCandidateEpochDays(startEpochDay, endEpochDay, selectedRange)
            var lastDrawnEndX = -Float.MAX_VALUE
            val minLabelSpacingPx = 8.dp.toPx()

            for (epochDay in candidateEpochDays) {
                val date = LocalDate.ofEpochDay(epochDay)
                val text = date.format(labelFormatter)
                val measured = textMeasurer.measure(text, style = axisLabelStyle)
                val labelWidth = measured.size.width.toFloat()
                var x = xForEpochDay(epochDay) - (labelWidth / 2f)

                // Clamp to canvas horizontal bounds
                if (x < paddingLeft) x = paddingLeft
                if (x + labelWidth > canvasWidth - paddingRight) {
                    x = canvasWidth - paddingRight - labelWidth
                }

                // Check collision with previously drawn label to guarantee no text overlap
                if (x >= lastDrawnEndX + minLabelSpacingPx) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = text,
                        topLeft = Offset(x, canvasHeight - paddingBottom + 6.dp.toPx()),
                        style = axisLabelStyle,
                    )
                    lastDrawnEndX = x + labelWidth
                }
            }

            // 5. Draw scrubber highlight and tooltip if active
            selectedPointIndex?.let { idx ->
                val selectedPoint = plotPoints.getOrNull(idx) ?: return@let
                val parsedDate = parsedPlotDates[idx]
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

/**
 * Computes candidate epoch days for drawing X-axis labels based on the range duration.
 */
private fun computeCandidateEpochDays(
    startEpochDay: Long,
    endEpochDay: Long,
    range: WeightRange,
): List<Long> {
    val totalDays = (endEpochDay - startEpochDay).coerceAtLeast(1)
    if (totalDays <= 2) {
        return listOf(startEpochDay, endEpochDay)
    }

    val numSegments = when (range) {
        WeightRange.LAST_7_DAYS -> 2 // start, mid, end (3 labels)
        WeightRange.LAST_15_DAYS -> 3 // 4 labels
        WeightRange.LAST_1_MONTH -> 3 // 4 labels
        WeightRange.LAST_6_MONTHS -> 5 // 6 labels
        WeightRange.LAST_1_YEAR -> 5 // 6 labels
        WeightRange.ALL -> 5 // 6 labels
    }

    val step = totalDays.toDouble() / numSegments.toDouble()
    val candidates = ArrayList<Long>(numSegments + 1)
    for (i in 0..numSegments) {
        val epochDay = (startEpochDay + (i * step)).toLong().coerceAtMost(endEpochDay)
        if (candidates.isEmpty() || epochDay > candidates.last()) {
            candidates.add(epochDay)
        }
    }
    return candidates
}

private fun findClosestPlotPointIndex(
    touchX: Float,
    canvasWidth: Float,
    paddingLeft: Float,
    paddingRight: Float,
    plotPoints: List<WeightPlotPoint>,
    parsedDates: List<LocalDate>,
): Int {
    if (plotPoints.isEmpty()) return 0
    val startDay = parsedDates.first().toEpochDay()
    val endDay = parsedDates.last().toEpochDay()
    val totalDays = (endDay - startDay).coerceAtLeast(1)

    val chartWidth = (canvasWidth - paddingLeft - paddingRight).coerceAtLeast(1f)

    var closestIndex = 0
    var minDiff = Float.MAX_VALUE

    for (i in plotPoints.indices) {
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
