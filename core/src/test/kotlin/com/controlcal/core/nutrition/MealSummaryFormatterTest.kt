package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class MealSummaryFormatterTest {

    @Test
    fun emptyListReturnsEmptyString() {
        assertEquals("", MealSummaryFormatter.formatSummary(emptyList()))
    }

    @Test
    fun singleEntryReturnsItsProductName() {
        val entry = MealEntry(
            id = 1L,
            date = "2026-10-15",
            meal = "BREAKFAST",
            productId = 1L,
            productName = "Avena integral",
            grams = 50.0,
            kcal = 180.0,
            protein = 6.0,
            carbs = 30.0,
            fat = 3.5,
            loggedAt = 1000L,
        )
        assertEquals("Avena integral", MealSummaryFormatter.formatSummary(listOf(entry)))
    }

    @Test
    fun multipleEntriesJoinedWithCommaAndDistinctNames() {
        val entry1 = MealEntry(id = 1L, date = "2026-10-15", meal = "BREAKFAST", productId = 1L, productName = "Café con leche", grams = 200.0, kcal = 80.0, protein = 6.0, carbs = 9.0, fat = 3.0, loggedAt = 1000L)
        val entry2 = MealEntry(id = 2L, date = "2026-10-15", meal = "BREAKFAST", productId = 2L, productName = "Tostada", grams = 60.0, kcal = 160.0, protein = 5.0, carbs = 30.0, fat = 1.0, loggedAt = 1001L)
        val entry3 = MealEntry(id = 3L, date = "2026-10-15", meal = "BREAKFAST", productId = 2L, productName = "Tostada", grams = 30.0, kcal = 80.0, protein = 2.5, carbs = 15.0, fat = 0.5, loggedAt = 1002L)

        assertEquals("Café con leche, Tostada", MealSummaryFormatter.formatSummary(listOf(entry1, entry2, entry3)))
    }

    @Test
    fun tareEntriesWithNegativeGramsAreExcludedFromPositiveSummary() {
        val entry1 = MealEntry(id = 1L, date = "2026-10-15", meal = "LUNCH", productId = 1L, productName = "Arroz", grams = 200.0, kcal = 260.0, protein = 5.0, carbs = 56.0, fat = 0.6, loggedAt = 1000L)
        val tare = MealEntry(id = 2L, date = "2026-10-15", meal = "LUNCH", productId = 1L, productName = "Arroz", grams = -50.0, kcal = -65.0, protein = -1.25, carbs = -14.0, fat = -0.15, loggedAt = 1001L)

        assertEquals("Arroz", MealSummaryFormatter.formatSummary(listOf(entry1, tare)))
    }

    @Test
    fun quickCaloriesEntryIsIncludedInSummary() {
        val normal = MealEntry(id = 1L, date = "2026-10-15", meal = "SNACK", productId = 1L, productName = "Manzana", grams = 150.0, kcal = 78.0, protein = 0.4, carbs = 21.0, fat = 0.3, loggedAt = 1000L)
        val quick = MealEntry(id = 2L, date = "2026-10-15", meal = "SNACK", productId = null, productName = "Café rápido", grams = 0.0, kcal = 120.0, protein = 3.0, carbs = 10.0, fat = 4.0, loggedAt = 1001L)

        assertEquals("Manzana, Café rápido", MealSummaryFormatter.formatSummary(listOf(normal, quick)))
    }
}
