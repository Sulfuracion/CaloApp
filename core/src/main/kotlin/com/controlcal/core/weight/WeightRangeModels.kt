package com.controlcal.core.weight

import com.controlcal.core.tdee.WeightTrendPoint

/**
 * A plottable point on the weight chart.
 *
 * @param date Local ISO date string (yyyy-MM-dd) representing this point or the representative date of the week.
 * @param weightKg Measured weight or weekly average weight in kilograms.
 * @param trendKg Smoothed EMA trend weight in kilograms on this date.
 * @param isWeeklyAverage True if this point aggregates multiple weigh-ins into a weekly average.
 * @param sampleCount Number of individual weigh-ins aggregated in this point (1 for non-aggregated).
 */
data class WeightPlotPoint(
    val date: String,
    val weightKg: Double,
    val trendKg: Double,
    val isWeeklyAverage: Boolean = false,
    val sampleCount: Int = 1,
)

/**
 * Summary of weight progress during the selected time range.
 *
 * @param trendChangeKg Difference in trend between the end and start of the range (end - start).
 * @param currentTrendKg The most recent trend value in the range.
 * @param weeklyRateKg Average rate of trend change per 7 days (kg/week).
 * @param range The selected time range.
 * @param sampleCount Total number of weigh-ins in this period.
 * @param hasSufficientData True if there are at least 2 weigh-ins to establish a period change and rate.
 */
data class WeightPeriodSummary(
    val trendChangeKg: Double,
    val currentTrendKg: Double,
    val weeklyRateKg: Double,
    val range: WeightRange,
    val sampleCount: Int,
    val hasSufficientData: Boolean,
)

/**
 * Complete chart dataset produced by [WeightRangeSelector].
 *
 * @param range Selected range.
 * @param trendPoints Full daily EMA trend points within the range (used to draw the continuous trend line).
 * @param plotPoints Plottable points (individual or weekly averaged dots).
 * @param periodSummary Period progress summary.
 * @param isWeeklyAggregated True if weekly aggregation was applied to [plotPoints].
 */
data class WeightChartData(
    val range: WeightRange,
    val trendPoints: List<WeightTrendPoint>,
    val plotPoints: List<WeightPlotPoint>,
    val periodSummary: WeightPeriodSummary,
    val isWeeklyAggregated: Boolean,
)
