package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MealEntryMergerTest {

    private val baseEntry = MealEntry(
        id = 10L,
        date = "2026-10-17",
        meal = "LUNCH",
        productId = 42L,
        productName = "Arroz cocido",
        grams = 100.0,
        kcal = 130.0,
        protein = 2.5,
        carbs = 28.0,
        fat = 0.3,
        loggedAt = 1000L,
    )

    @Test
    fun canMerge_returnsTrueWhenDateMealAndProductIdMatchAndBothHavePositiveGrams() {
        val incoming = baseEntry.copy(
            id = 0L,
            grams = 50.0,
            kcal = 65.0,
            protein = 1.25,
            carbs = 14.0,
            fat = 0.15,
            loggedAt = 2000L,
        )

        assertTrue(MealEntryMerger.canMerge(baseEntry, incoming))
    }

    @Test
    fun canMerge_returnsFalseForTareEntryWithNegativeGrams() {
        val tareExisting = baseEntry.copy(grams = -30.0)
        val tareIncoming = baseEntry.copy(id = 0L, grams = -20.0)
        val normalIncoming = baseEntry.copy(id = 0L, grams = 50.0)

        // Existing is tare
        assertFalse(MealEntryMerger.canMerge(tareExisting, normalIncoming))
        // Incoming is tare
        assertFalse(MealEntryMerger.canMerge(baseEntry, tareIncoming))
        // Both tare
        assertFalse(MealEntryMerger.canMerge(tareExisting, tareIncoming))
    }

    @Test
    fun canMerge_returnsFalseForQuickCaloriesWhereProductIdIsNull() {
        val quickCaloriesExisting = baseEntry.copy(productId = null, productName = "Calorías rápidas")
        val quickCaloriesIncoming = baseEntry.copy(id = 0L, productId = null, productName = "Calorías rápidas")
        val normalIncoming = baseEntry.copy(id = 0L, grams = 50.0)

        // Existing is quick calories
        assertFalse(MealEntryMerger.canMerge(quickCaloriesExisting, normalIncoming))
        // Incoming is quick calories
        assertFalse(MealEntryMerger.canMerge(baseEntry, quickCaloriesIncoming))
        // Both are quick calories
        assertFalse(MealEntryMerger.canMerge(quickCaloriesExisting, quickCaloriesIncoming))
    }

    @Test
    fun canMerge_returnsFalseForDifferentMeal() {
        val incomingDifferentMeal = baseEntry.copy(id = 0L, meal = "DINNER")
        assertFalse(MealEntryMerger.canMerge(baseEntry, incomingDifferentMeal))
    }

    @Test
    fun canMerge_returnsFalseForDifferentDate() {
        val incomingDifferentDate = baseEntry.copy(id = 0L, date = "2026-10-18")
        assertFalse(MealEntryMerger.canMerge(baseEntry, incomingDifferentDate))
    }

    @Test
    fun canMerge_returnsFalseForDifferentProductId() {
        val incomingDifferentProduct = baseEntry.copy(id = 0L, productId = 99L, productName = "Pollo")
        assertFalse(MealEntryMerger.canMerge(baseEntry, incomingDifferentProduct))
    }

    @Test
    fun findMergeCandidate_selectsEarliestEntryByLoggedAtAmongMultipleDuplicates() {
        val earliest = baseEntry.copy(id = 1L, loggedAt = 500L)
        val middle = baseEntry.copy(id = 2L, loggedAt = 1000L)
        val latest = baseEntry.copy(id = 3L, loggedAt = 1500L)

        val incoming = baseEntry.copy(id = 0L, loggedAt = 2000L)
        val candidates = listOf(latest, middle, earliest)

        val chosen = MealEntryMerger.findMergeCandidate(candidates, incoming)
        assertNotNull(chosen)
        assertEquals(1L, chosen!!.id)
        assertEquals(500L, chosen.loggedAt)
    }

    @Test
    fun findMergeCandidate_breaksTiesInLoggedAtUsingLowestId() {
        val entry1 = baseEntry.copy(id = 5L, loggedAt = 1000L)
        val entry2 = baseEntry.copy(id = 2L, loggedAt = 1000L)

        val incoming = baseEntry.copy(id = 0L, loggedAt = 2000L)
        val chosen = MealEntryMerger.findMergeCandidate(listOf(entry1, entry2), incoming)
        assertNotNull(chosen)
        assertEquals(2L, chosen!!.id)
    }

    @Test
    fun findMergeCandidate_returnsNullWhenNoMatchingCandidateExists() {
        val differentMeal = baseEntry.copy(id = 1L, meal = "BREAKFAST")
        val tare = baseEntry.copy(id = 2L, grams = -50.0)
        val quickCalories = baseEntry.copy(id = 3L, productId = null)

        val incoming = baseEntry.copy(id = 0L)
        val chosen = MealEntryMerger.findMergeCandidate(listOf(differentMeal, tare, quickCalories), incoming)
        assertNull(chosen)
    }

    @Test
    fun merge_sumsGramsKcalProteinCarbsAndFatAndPreservesExistingIdAndEarliestLoggedAt() {
        val incoming = baseEntry.copy(
            id = 0L,
            grams = 50.0,
            kcal = 65.0,
            protein = 1.25,
            carbs = 14.0,
            fat = 0.15,
            loggedAt = 2000L,
        )

        val merged = MealEntryMerger.merge(baseEntry, incoming)

        assertEquals(baseEntry.id, merged.id)
        assertEquals(baseEntry.date, merged.date)
        assertEquals(baseEntry.meal, merged.meal)
        assertEquals(baseEntry.productId, merged.productId)
        assertEquals(baseEntry.productName, merged.productName)
        assertEquals(150.0, merged.grams, 0.001)
        assertEquals(195.0, merged.kcal, 0.001)
        assertEquals(3.75, merged.protein, 0.001)
        assertEquals(42.0, merged.carbs, 0.001)
        assertEquals(0.45, merged.fat, 0.001)
        assertEquals(1000L, merged.loggedAt)
    }

    @Test
    fun merge_preservesOldEntrySnapshotWhenProductWasEditedBetweenAdditions() {
        // Entry 1 was saved when product had 200 kcal / 100g (P: 10, C: 20, G: 5)
        val oldEntry = MealEntry(
            id = 1L,
            date = "2026-10-17",
            meal = "LUNCH",
            productId = 42L,
            productName = "Barrita energética",
            grams = 100.0,
            kcal = 200.0,
            protein = 10.0,
            carbs = 20.0,
            fat = 5.0,
            loggedAt = 1000L,
        )

        // Product was edited in database to 300 kcal / 100g (P: 15, C: 30, G: 10).
        // New entry is calculated based on current product:
        val newEntry = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "LUNCH",
            productId = 42L,
            productName = "Barrita energética (reformulación)",
            grams = 100.0,
            kcal = 300.0,
            protein = 15.0,
            carbs = 30.0,
            fat = 10.0,
            loggedAt = 2000L,
        )

        val merged = MealEntryMerger.merge(oldEntry, newEntry)

        // Total should be 100 + 100 = 200g
        assertEquals(200.0, merged.grams, 0.001)
        // Kcal should strictly be 200 + 300 = 500 kcal (NOT 600 kcal which would happen if old part was recalculated)
        assertEquals(500.0, merged.kcal, 0.001)
        assertEquals(25.0, merged.protein, 0.001)
        assertEquals(50.0, merged.carbs, 0.001)
        assertEquals(15.0, merged.fat, 0.001)
    }

    @Test
    fun merge_throwsIllegalArgumentExceptionWhenCanMergeIsFalse() {
        val tare = baseEntry.copy(id = 0L, grams = -50.0)
        try {
            MealEntryMerger.merge(baseEntry, tare)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Success
        }
    }
}
