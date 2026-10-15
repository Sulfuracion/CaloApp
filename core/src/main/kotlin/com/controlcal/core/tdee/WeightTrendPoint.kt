package com.controlcal.core.tdee

/**
 * A data point pairing a measured weight with its calculated exponential moving average trend.
 * [date] is in local ISO format (yyyy-MM-dd).
 * [weightKg] is the raw logged weight for this date.
 * [trendKg] is the smoothed exponential moving average value.
 */
data class WeightTrendPoint(
    val date: String,
    val weightKg: Double,
    val trendKg: Double,
)
