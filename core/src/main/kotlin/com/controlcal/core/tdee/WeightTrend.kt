package com.controlcal.core.tdee

import com.controlcal.core.model.WeightEntry

/**
 * Calculates exponential moving average (EMA) trends for body weight tracking.
 * Amortizes daily fluctuations (water retention, sodium, digestion) to reveal
 * the true underlying physiological weight trend.
 */
object WeightTrend {

    /**
     * Default smoothing factor (alpha) for body weight exponential moving average.
     * Alpha = 0.1 corresponds to the canonical smoothing parameter from The Hacker's Diet,
     * damping daily fluid fluctuations while capturing genuine weight changes over ~10-20 days.
     */
    const val DEFAULT_ALPHA = 0.1

    /**
     * Calculates the exponential moving average (EMA) trend series for a list of weight entries.
     *
     * The algorithm:
     * 1. Filters out non-positive weights (<= 0.0) as invalid.
     * 2. Sorts entries chronologically by date.
     * 3. For the first entry, trend_0 = weight_0.
     * 4. For subsequent entries, trend_i = alpha * weight_i + (1 - alpha) * trend_{i-1}.
     *
     * @param entries Raw weight measurements.
     * @param alpha Smoothing factor, must be in the range (0.0, 1.0]. Defaults to [DEFAULT_ALPHA].
     * @return List of [WeightTrendPoint] ordered chronologically by date.
     */
    fun calculateTrend(
        entries: List<WeightEntry>,
        alpha: Double = DEFAULT_ALPHA,
    ): List<WeightTrendPoint> {
        require(alpha > 0.0 && alpha <= 1.0) {
            "Alpha smoothing factor must be in (0.0, 1.0], but was $alpha"
        }
        val validEntries = entries
            .filter { it.weightKg > 0.0 }
            .sortedBy { it.date }

        if (validEntries.isEmpty()) return emptyList()

        val result = ArrayList<WeightTrendPoint>(validEntries.size)
        var currentTrend = validEntries[0].weightKg
        result.add(
            WeightTrendPoint(
                date = validEntries[0].date,
                weightKg = validEntries[0].weightKg,
                trendKg = currentTrend,
            )
        )

        for (i in 1 until validEntries.size) {
            val entry = validEntries[i]
            currentTrend = (alpha * entry.weightKg) + ((1.0 - alpha) * currentTrend)
            result.add(
                WeightTrendPoint(
                    date = entry.date,
                    weightKg = entry.weightKg,
                    trendKg = currentTrend,
                )
            )
        }

        return result
    }
}
