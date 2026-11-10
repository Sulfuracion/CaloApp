package com.controlcal.core.weight

import com.controlcal.core.model.WeightEntry
import com.controlcal.core.tdee.WeightTrend
import com.controlcal.core.tdee.WeightTrendPoint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure functions for weight chart range selection, EMA trimming, weekly aggregation,
 * and period summary calculations.
 */
object WeightRangeSelector {

    /**
     * Default threshold of raw weigh-ins in 6M, 1A, or ALL before weekly grouping is enabled.
     */
    const val DEFAULT_GROUPING_THRESHOLD = 25

    /**
     * Selects and prepares all chart data for the requested [range] relative to [referenceDate].
     *
     * IMPORTANT: The EMA trend is ALWAYS computed on the complete chronological series of weigh-ins
     * first, and only then trimmed to the range window. This preserves historical momentum and
     * prevents artificial resets at the beginning of the curve.
     *
     * @param allEntries Full historical list of logged weigh-ins.
     * @param range Selected time range.
     * @param referenceDate Anchor date (typically today).
     * @param groupingThreshold Number of points above which weekly aggregation triggers for 6M, 1A, and ALL.
     */
    fun selectChartData(
        allEntries: List<WeightEntry>,
        range: WeightRange,
        referenceDate: LocalDate,
        groupingThreshold: Int = DEFAULT_GROUPING_THRESHOLD,
    ): WeightChartData {
        // 1. Compute EMA over the FULL series
        val fullTrend = WeightTrend.calculateTrend(allEntries)

        // 2. Trim EMA trend to range window
        val trimmedTrend = trimTrendToRange(fullTrend, range, referenceDate)
        val trendMap = trimmedTrend.associate { it.date to it.trendKg }

        // 3. Filter raw entries falling within the range
        val entriesInRange = allEntries
            .filter { it.weightKg > 0.0 && it.date in trendMap }
            .sortedBy { it.date }

        // 4. Determine if weekly aggregation applies
        val canAggregate = range == WeightRange.LAST_6_MONTHS ||
            range == WeightRange.LAST_1_YEAR ||
            range == WeightRange.ALL
        val isWeeklyAggregated = canAggregate && entriesInRange.size > groupingThreshold

        // 5. Generate plot points (individual or weekly averaged)
        val plotPoints = preparePlotPoints(
            entriesInRange = entriesInRange,
            trendMap = trendMap,
            isWeeklyAggregated = isWeeklyAggregated,
        )

        // 6. Compute period summary
        val periodSummary = calculatePeriodSummary(
            trimmedTrend = trimmedTrend,
            sampleCount = entriesInRange.size,
            range = range,
        )

        return WeightChartData(
            range = range,
            trendPoints = trimmedTrend,
            plotPoints = plotPoints,
            periodSummary = periodSummary,
            isWeeklyAggregated = isWeeklyAggregated,
        )
    }

    /**
     * Trims a precomputed full [trend] list to the date window determined by [range] and [referenceDate].
     */
    fun trimTrendToRange(
        trend: List<WeightTrendPoint>,
        range: WeightRange,
        referenceDate: LocalDate,
    ): List<WeightTrendPoint> {
        if (trend.isEmpty()) return emptyList()

        val startDate = calculateStartDate(range, referenceDate)
        val refDateStr = referenceDate.toString()

        return trend.filter { point ->
            val withinStart = startDate == null || point.date >= startDate.toString()
            val withinEnd = point.date <= refDateStr
            withinStart && withinEnd
        }
    }

    /**
     * Calculates the start date cutoff for a given [range] from [referenceDate].
     * Returns null for [WeightRange.ALL].
     */
    fun calculateStartDate(range: WeightRange, referenceDate: LocalDate): LocalDate? {
        return when (range) {
            WeightRange.LAST_7_DAYS -> referenceDate.minusDays(7)
            WeightRange.LAST_15_DAYS -> referenceDate.minusDays(15)
            WeightRange.LAST_1_MONTH -> referenceDate.minusMonths(1)
            WeightRange.LAST_6_MONTHS -> referenceDate.minusMonths(6)
            WeightRange.LAST_1_YEAR -> referenceDate.minusYears(1)
            WeightRange.ALL -> null
        }
    }

    /**
     * Prepares plottable points from [entriesInRange]. If [isWeeklyAggregated] is true, groups entries
     * by calendar week (Monday to Sunday) and averages their weights.
     */
    fun preparePlotPoints(
        entriesInRange: List<WeightEntry>,
        trendMap: Map<String, Double>,
        isWeeklyAggregated: Boolean,
    ): List<WeightPlotPoint> {
        if (entriesInRange.isEmpty()) return emptyList()

        if (!isWeeklyAggregated) {
            return entriesInRange.map { entry ->
                WeightPlotPoint(
                    date = entry.date,
                    weightKg = entry.weightKg,
                    trendKg = trendMap[entry.date] ?: entry.weightKg,
                    isWeeklyAverage = false,
                    sampleCount = 1,
                )
            }
        }

        // Group by calendar week (ISO Monday)
        val weeklyGroups = entriesInRange.groupBy { entry ->
            val date = LocalDate.parse(entry.date)
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }

        return weeklyGroups.keys.sorted().map { monday ->
            val groupEntries = weeklyGroups.getValue(monday).sortedBy { it.date }
            val avgWeight = groupEntries.map { it.weightKg }.average()
            val representativeDate = groupEntries.last().date
            val trendValue = trendMap[representativeDate] ?: avgWeight

            WeightPlotPoint(
                date = representativeDate,
                weightKg = avgWeight,
                trendKg = trendValue,
                isWeeklyAverage = true,
                sampleCount = groupEntries.size,
            )
        }
    }

    /**
     * Computes the period summary metrics based on the trimmed trend series.
     */
    fun calculatePeriodSummary(
        trimmedTrend: List<WeightTrendPoint>,
        sampleCount: Int,
        range: WeightRange,
    ): WeightPeriodSummary {
        if (trimmedTrend.size < 2) {
            val current = trimmedTrend.lastOrNull()?.trendKg ?: 0.0
            return WeightPeriodSummary(
                trendChangeKg = 0.0,
                currentTrendKg = current,
                weeklyRateKg = 0.0,
                range = range,
                sampleCount = sampleCount,
                hasSufficientData = false,
            )
        }

        val first = trimmedTrend.first()
        val last = trimmedTrend.last()
        val trendChangeKg = last.trendKg - first.trendKg
        val currentTrendKg = last.trendKg

        val firstDate = LocalDate.parse(first.date)
        val lastDate = LocalDate.parse(last.date)
        val daysBetween = ChronoUnit.DAYS.between(firstDate, lastDate)

        val weeklyRateKg = if (daysBetween >= 1) {
            (trendChangeKg * 7.0) / daysBetween.toDouble()
        } else {
            0.0
        }

        return WeightPeriodSummary(
            trendChangeKg = trendChangeKg,
            currentTrendKg = currentTrendKg,
            weeklyRateKg = weeklyRateKg,
            range = range,
            sampleCount = sampleCount,
            hasSufficientData = true,
        )
    }
}
