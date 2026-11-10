package com.controlcal.core.weight

import com.controlcal.core.model.WeightEntry
import com.controlcal.core.tdee.WeightTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightRangeSelectorTest {

    private val refDate = LocalDate.parse("2026-11-10")

    @Test
    fun trimTrendToRange_7days_filtersDatesCorrectly() {
        val trend = listOf(
            WeightTrend.calculateTrend(listOf(WeightEntry("2026-11-01", 80.0))).first(),
            WeightTrend.calculateTrend(listOf(WeightEntry("2026-11-02", 80.0))).first(),
            WeightTrend.calculateTrend(listOf(WeightEntry("2026-11-03", 80.0))).first(), // 7 days before
            WeightTrend.calculateTrend(listOf(WeightEntry("2026-11-08", 80.0))).first(),
            WeightTrend.calculateTrend(listOf(WeightEntry("2026-11-10", 80.0))).first(),
        )

        val trimmed = WeightRangeSelector.trimTrendToRange(trend, WeightRange.LAST_7_DAYS, refDate)
        assertEquals(listOf("2026-11-03", "2026-11-08", "2026-11-10"), trimmed.map { it.date })
    }

    @Test
    fun trimTrendToRange_15days_filtersDatesCorrectly() {
        val entries = listOf(
            WeightEntry("2026-10-20", 80.0),
            WeightEntry("2026-10-26", 80.0), // 15 days before refDate (2026-11-10 - 15d = 2026-10-26)
            WeightEntry("2026-11-01", 80.0),
            WeightEntry("2026-11-10", 80.0),
        )
        val trend = WeightTrend.calculateTrend(entries)
        val trimmed = WeightRangeSelector.trimTrendToRange(trend, WeightRange.LAST_15_DAYS, refDate)

        assertEquals(listOf("2026-10-26", "2026-11-01", "2026-11-10"), trimmed.map { it.date })
    }

    @Test
    fun trimTrendToRange_1month_filtersDatesCorrectly() {
        val entries = listOf(
            WeightEntry("2026-10-09", 80.0),
            WeightEntry("2026-10-10", 80.0), // 1 month before (2026-11-10 - 1m = 2026-10-10)
            WeightEntry("2026-10-25", 80.0),
            WeightEntry("2026-11-10", 80.0),
        )
        val trend = WeightTrend.calculateTrend(entries)
        val trimmed = WeightRangeSelector.trimTrendToRange(trend, WeightRange.LAST_1_MONTH, refDate)

        assertEquals(listOf("2026-10-10", "2026-10-25", "2026-11-10"), trimmed.map { it.date })
    }

    @Test
    fun trimTrendToRange_6months_filtersDatesCorrectly() {
        val entries = listOf(
            WeightEntry("2026-05-09", 80.0),
            WeightEntry("2026-05-10", 80.0), // 6 months before (2026-11-10 - 6m = 2026-05-10)
            WeightEntry("2026-08-01", 80.0),
            WeightEntry("2026-11-10", 80.0),
        )
        val trend = WeightTrend.calculateTrend(entries)
        val trimmed = WeightRangeSelector.trimTrendToRange(trend, WeightRange.LAST_6_MONTHS, refDate)

        assertEquals(listOf("2026-05-10", "2026-08-01", "2026-11-10"), trimmed.map { it.date })
    }

    @Test
    fun trimTrendToRange_1year_filtersDatesCorrectly() {
        val entries = listOf(
            WeightEntry("2025-11-09", 80.0),
            WeightEntry("2025-11-10", 80.0), // 1 year before
            WeightEntry("2026-05-10", 80.0),
            WeightEntry("2026-11-10", 80.0),
        )
        val trend = WeightTrend.calculateTrend(entries)
        val trimmed = WeightRangeSelector.trimTrendToRange(trend, WeightRange.LAST_1_YEAR, refDate)

        assertEquals(listOf("2025-11-10", "2026-05-10", "2026-11-10"), trimmed.map { it.date })
    }

    @Test
    fun trimTrendToRange_all_returnsAllPointsWithoutCutoff() {
        val entries = listOf(
            WeightEntry("2020-01-01", 85.0),
            WeightEntry("2024-06-15", 82.0),
            WeightEntry("2026-11-10", 78.0),
        )
        val trend = WeightTrend.calculateTrend(entries)
        val trimmed = WeightRangeSelector.trimTrendToRange(trend, WeightRange.ALL, refDate)

        assertEquals(3, trimmed.size)
        assertEquals(listOf("2020-01-01", "2024-06-15", "2026-11-10"), trimmed.map { it.date })
    }

    @Test
    fun emaCalculatedOnFullSeriesDiffersFromEmaOnRangeOnly() {
        // Construct 40 daily weigh-ins where weight drops from 100 kg to 80 kg over the first 30 days,
        // and then stays constant at 80 kg for the final 10 days.
        val fullEntries = ArrayList<WeightEntry>()
        val startDay = LocalDate.parse("2026-10-01")

        for (i in 0 until 30) {
            val dateStr = startDay.plusDays(i.toLong()).toString()
            val weight = 100.0 - (20.0 * (i.toDouble() / 29.0))
            fullEntries.add(WeightEntry(dateStr, weight))
        }
        for (i in 30 until 40) {
            val dateStr = startDay.plusDays(i.toLong()).toString()
            fullEntries.add(WeightEntry(dateStr, 80.0))
        }

        val anchorDate = startDay.plusDays(39) // Last day
        // 1. Full series EMA trimmed to 7 days
        val fullSeriesChartData = WeightRangeSelector.selectChartData(
            allEntries = fullEntries,
            range = WeightRange.LAST_7_DAYS,
            referenceDate = anchorDate,
        )

        // 2. Trend calculated ONLY with the entries inside the 7-day range
        val rangeOnlyEntries = fullEntries.filter {
            it.date >= anchorDate.minusDays(7).toString() && it.date <= anchorDate.toString()
        }
        val rangeOnlyTrend = WeightTrend.calculateTrend(rangeOnlyEntries)

        // At the start of the 7-day window, rangeOnlyTrend resets to weight_0 = 80.0 kg
        // because it has no knowledge of the historical drop from 100 kg.
        // Full series EMA has smoothed momentum and is still slightly above 80.0 kg (e.g. ~81.2 kg).
        val firstFullTrendPoint = fullSeriesChartData.trendPoints.first()
        val firstRangeOnlyTrendPoint = rangeOnlyTrend.first()

        assertEquals(firstFullTrendPoint.date, firstRangeOnlyTrendPoint.date)
        assertEquals(80.0, firstRangeOnlyTrendPoint.trendKg, 0.001)
        assertNotEquals(firstFullTrendPoint.trendKg, firstRangeOnlyTrendPoint.trendKg, 0.01)
        assertTrue(
            "Full series trend (${firstFullTrendPoint.trendKg}) should exceed range-only trend (${firstRangeOnlyTrendPoint.trendKg}) due to momentum",
            firstFullTrendPoint.trendKg > firstRangeOnlyTrendPoint.trendKg
        )
    }

    @Test
    fun weeklyGrouping_groupsEntriesByCalendarWeekAndComputesAverage() {
        // Week 1: 2026-11-02 (Mon) to 2026-11-08 (Sun) -> 3 entries
        // Week 2: 2026-11-09 (Mon) to 2026-11-15 (Sun) -> 2 entries
        val entries = listOf(
            WeightEntry("2026-11-02", 80.0),
            WeightEntry("2026-11-04", 81.0),
            WeightEntry("2026-11-06", 82.0),
            WeightEntry("2026-11-09", 79.0),
            WeightEntry("2026-11-11", 81.0),
        )
        val trendMap = entries.associate { it.date to it.weightKg }

        val plotPoints = WeightRangeSelector.preparePlotPoints(
            entriesInRange = entries,
            trendMap = trendMap,
            isWeeklyAggregated = true,
        )

        assertEquals(2, plotPoints.size)

        // Week 1 avg: (80 + 81 + 82) / 3 = 81.0, representative date = 2026-11-06
        val week1 = plotPoints[0]
        assertEquals("2026-11-06", week1.date)
        assertEquals(81.0, week1.weightKg, 0.001)
        assertTrue(week1.isWeeklyAverage)
        assertEquals(3, week1.sampleCount)

        // Week 2 avg: (79 + 81) / 2 = 80.0, representative date = 2026-11-11
        val week2 = plotPoints[1]
        assertEquals("2026-11-11", week2.date)
        assertEquals(80.0, week2.weightKg, 0.001)
        assertTrue(week2.isWeeklyAverage)
        assertEquals(2, week2.sampleCount)
    }

    @Test
    fun weeklyGrouping_notAppliedWhenPointsBelowThreshold() {
        // 5 points in 6 months with threshold = 25 -> should NOT aggregate
        val entries = listOf(
            WeightEntry("2026-06-01", 85.0),
            WeightEntry("2026-07-01", 84.0),
            WeightEntry("2026-08-01", 83.0),
            WeightEntry("2026-09-01", 82.0),
            WeightEntry("2026-10-01", 81.0),
        )
        val chartData = WeightRangeSelector.selectChartData(
            allEntries = entries,
            range = WeightRange.LAST_6_MONTHS,
            referenceDate = LocalDate.parse("2026-10-15"),
            groupingThreshold = 25,
        )

        assertFalse(chartData.isWeeklyAggregated)
        assertEquals(5, chartData.plotPoints.size)
        assertFalse(chartData.plotPoints.first().isWeeklyAverage)
    }

    @Test
    fun weeklyGrouping_neverAppliedToShortRanges() {
        // 30 daily points in LAST_1_MONTH with threshold = 10 -> MUST NOT aggregate
        val entries = (0 until 30).map { i ->
            WeightEntry(LocalDate.parse("2026-10-10").plusDays(i.toLong()).toString(), 80.0 + (i * 0.1))
        }

        val chartData = WeightRangeSelector.selectChartData(
            allEntries = entries,
            range = WeightRange.LAST_1_MONTH,
            referenceDate = LocalDate.parse("2026-11-10"),
            groupingThreshold = 10,
        )

        assertFalse(chartData.isWeeklyAggregated)
        assertEquals(30, chartData.plotPoints.size)
        assertFalse(chartData.plotPoints.first().isWeeklyAverage)
    }

    @Test
    fun periodSummary_calculatesTrendChangeCurrentTrendAndWeeklyRate() {
        // Trend points spanning 14 days with trend change of -1.4 kg
        val d1 = "2026-10-01"
        val d2 = "2026-10-15"
        val trendPoints = listOf(
            WeightTrend.calculateTrend(listOf(WeightEntry(d1, 80.0))).first(),
            WeightTrend.calculateTrend(listOf(WeightEntry(d2, 78.6))).first(),
        )

        val summary = WeightRangeSelector.calculatePeriodSummary(
            trimmedTrend = trendPoints,
            sampleCount = 2,
            range = WeightRange.LAST_15_DAYS,
        )

        assertTrue(summary.hasSufficientData)
        assertEquals(-1.4, summary.trendChangeKg, 0.001)
        assertEquals(78.6, summary.currentTrendKg, 0.001)
        // Rate = -1.4 * 7 / 14 = -0.7 kg/week
        assertEquals(-0.7, summary.weeklyRateKg, 0.001)
        assertEquals(2, summary.sampleCount)
    }

    @Test
    fun periodSummary_handlesEmptyAndSingleEntryGracefully() {
        // 1. Empty list
        val emptySummary = WeightRangeSelector.calculatePeriodSummary(
            trimmedTrend = emptyList(),
            sampleCount = 0,
            range = WeightRange.LAST_7_DAYS,
        )
        assertFalse(emptySummary.hasSufficientData)
        assertEquals(0.0, emptySummary.trendChangeKg, 0.001)
        assertEquals(0.0, emptySummary.currentTrendKg, 0.001)
        assertEquals(0.0, emptySummary.weeklyRateKg, 0.001)
        assertEquals(0, emptySummary.sampleCount)

        // 2. Single entry
        val singleTrend = listOf(WeightTrend.calculateTrend(listOf(WeightEntry("2026-11-01", 75.0))).first())
        val singleSummary = WeightRangeSelector.calculatePeriodSummary(
            trimmedTrend = singleTrend,
            sampleCount = 1,
            range = WeightRange.LAST_7_DAYS,
        )
        assertFalse(singleSummary.hasSufficientData)
        assertEquals(0.0, singleSummary.trendChangeKg, 0.001)
        assertEquals(75.0, singleSummary.currentTrendKg, 0.001)
        assertEquals(0.0, singleSummary.weeklyRateKg, 0.001)
        assertEquals(1, singleSummary.sampleCount)
    }
}
