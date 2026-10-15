package com.controlcal.core.tdee

import com.controlcal.core.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightTrendTest {

    @Test
    fun emptyListReturnsEmptyList() {
        val result = WeightTrend.calculateTrend(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun singleEntryReturnsTrendEqualToWeight() {
        val entries = listOf(WeightEntry(date = "2026-10-01", weightKg = 75.4))
        val result = WeightTrend.calculateTrend(entries)

        assertEquals(1, result.size)
        assertEquals("2026-10-01", result[0].date)
        assertEquals(75.4, result[0].weightKg, 0.0001)
        assertEquals(75.4, result[0].trendKg, 0.0001)
    }

    @Test
    fun constantWeightsProduceFlatTrend() {
        val entries = listOf(
            WeightEntry(date = "2026-10-01", weightKg = 80.0),
            WeightEntry(date = "2026-10-02", weightKg = 80.0),
            WeightEntry(date = "2026-10-03", weightKg = 80.0),
        )
        val result = WeightTrend.calculateTrend(entries)

        assertEquals(3, result.size)
        for (point in result) {
            assertEquals(80.0, point.weightKg, 0.0001)
            assertEquals(80.0, point.trendKg, 0.0001)
        }
    }

    @Test
    fun exponentialSmoothingFollowsEmaFormula() {
        // T_0 = 80.0
        // T_1 = 0.1 * 85.0 + 0.9 * 80.0 = 8.5 + 72.0 = 80.5
        // T_2 = 0.1 * 85.0 + 0.9 * 80.5 = 8.5 + 72.45 = 80.95
        val entries = listOf(
            WeightEntry(date = "2026-10-01", weightKg = 80.0),
            WeightEntry(date = "2026-10-02", weightKg = 85.0),
            WeightEntry(date = "2026-10-03", weightKg = 85.0),
        )
        val result = WeightTrend.calculateTrend(entries, alpha = 0.1)

        assertEquals(3, result.size)
        assertEquals(80.0, result[0].trendKg, 0.0001)
        assertEquals(80.5, result[1].trendKg, 0.0001)
        assertEquals(80.95, result[2].trendKg, 0.0001)
    }

    @Test
    fun unsortedInputIsSortedChronologically() {
        val entries = listOf(
            WeightEntry(date = "2026-10-03", weightKg = 82.0),
            WeightEntry(date = "2026-10-01", weightKg = 80.0),
            WeightEntry(date = "2026-10-02", weightKg = 81.0),
        )
        val result = WeightTrend.calculateTrend(entries)

        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03"), result.map { it.date })
        assertEquals(80.0, result[0].trendKg, 0.0001)
        // 0.1 * 81.0 + 0.9 * 80.0 = 80.1
        assertEquals(80.1, result[1].trendKg, 0.0001)
    }

    @Test
    fun nonPositiveWeightsAreFilteredOut() {
        val entries = listOf(
            WeightEntry(date = "2026-10-01", weightKg = 80.0),
            WeightEntry(date = "2026-10-02", weightKg = 0.0),
            WeightEntry(date = "2026-10-03", weightKg = -5.0),
            WeightEntry(date = "2026-10-04", weightKg = 81.0),
        )
        val result = WeightTrend.calculateTrend(entries)

        assertEquals(2, result.size)
        assertEquals("2026-10-01", result[0].date)
        assertEquals("2026-10-04", result[1].date)
    }

    @Test
    fun invalidAlphaThrowsIllegalArgumentException() {
        val entries = listOf(WeightEntry(date = "2026-10-01", weightKg = 80.0))

        assertThrows(IllegalArgumentException::class.java) {
            WeightTrend.calculateTrend(entries, alpha = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WeightTrend.calculateTrend(entries, alpha = -0.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WeightTrend.calculateTrend(entries, alpha = 1.1)
        }
    }

    @Test
    fun alphaOneMatchesRawWeightsExactly() {
        val entries = listOf(
            WeightEntry(date = "2026-10-01", weightKg = 80.0),
            WeightEntry(date = "2026-10-02", weightKg = 82.5),
            WeightEntry(date = "2026-10-03", weightKg = 79.2),
        )
        val result = WeightTrend.calculateTrend(entries, alpha = 1.0)

        assertEquals(entries.map { it.weightKg }, result.map { it.trendKg })
    }
}
