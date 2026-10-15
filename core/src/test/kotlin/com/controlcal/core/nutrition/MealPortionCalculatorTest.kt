package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPortionCalculatorTest {

    @Test
    fun calculateStep_prioritizesUnitGOverServingG() {
        val step = MealPortionCalculator.calculateStep(unitG = 55.0, servingG = 30.0)
        assertEquals(55.0, step, 0.001)
    }

    @Test
    fun calculateStep_usesServingGWhenUnitGIsNull() {
        val step = MealPortionCalculator.calculateStep(unitG = null, servingG = 30.0)
        assertEquals(30.0, step, 0.001)
    }

    @Test
    fun calculateStep_usesServingGWhenUnitGIsZeroOrNegative() {
        val step = MealPortionCalculator.calculateStep(unitG = 0.0, servingG = 25.0)
        assertEquals(25.0, step, 0.001)
    }

    @Test
    fun calculateStep_fallsBackToTenGramsWhenNeitherAvailable() {
        val stepNulls = MealPortionCalculator.calculateStep(unitG = null, servingG = null)
        assertEquals(10.0, stepNulls, 0.001)

        val stepZeros = MealPortionCalculator.calculateStep(unitG = 0.0, servingG = 0.0)
        assertEquals(10.0, stepZeros, 0.001)
    }

    @Test
    fun increaseQuantity_scalesProportionallyByStep() {
        val entry = MealEntry(
            id = 1L,
            date = "2026-10-15",
            meal = "BREAKFAST",
            productId = 10L,
            productName = "Avena",
            grams = 50.0,
            kcal = 185.0,
            protein = 6.5,
            carbs = 33.0,
            fat = 3.5,
            loggedAt = 1000L,
        )

        val updated = MealPortionCalculator.increaseQuantity(entry, step = 25.0)

        assertEquals(75.0, updated.grams, 0.001)
        // factor = 75 / 50 = 1.5
        assertEquals(277.5, updated.kcal, 0.001)
        assertEquals(9.75, updated.protein, 0.001)
        assertEquals(49.5, updated.carbs, 0.001)
        assertEquals(5.25, updated.fat, 0.001)
    }

    @Test
    fun decreaseQuantity_clampsAtOneStepAndNeverDropsBelow() {
        val entry = MealEntry(
            id = 1L,
            date = "2026-10-15",
            meal = "BREAKFAST",
            productId = 10L,
            productName = "Pan",
            grams = 30.0,
            kcal = 75.0,
            protein = 2.4,
            carbs = 14.7,
            fat = 0.9,
            loggedAt = 1000L,
        )

        // Step is 20g. 30g - 20g = 10g, but minimum is step (20g).
        val decreased = MealPortionCalculator.decreaseQuantity(entry, step = 20.0)
        assertEquals(20.0, decreased.grams, 0.001)
        assertEquals(50.0, decreased.kcal, 0.001)

        // Decreasing again from 20g with step 20g stays at 20g (cannot decrease below 1 step)
        val decreasedAgain = MealPortionCalculator.decreaseQuantity(decreased, step = 20.0)
        assertEquals(20.0, decreasedAgain.grams, 0.001)
        assertEquals(50.0, decreasedAgain.kcal, 0.001)
    }

    @Test
    fun canDecrease_returnsFalseWhenAtOrEqualToStep() {
        assertFalse(MealPortionCalculator.canDecrease(grams = 10.0, step = 10.0))
        assertFalse(MealPortionCalculator.canDecrease(grams = 5.0, step = 10.0))
        assertTrue(MealPortionCalculator.canDecrease(grams = 20.0, step = 10.0))
        assertTrue(MealPortionCalculator.canDecrease(grams = 10.5, step = 10.0))
    }

    @Test
    fun rescaleEntry_whenProductWasEditedAfterLogging_preservesEntryNutritionalDensity() {
        // Entry originally registered with product A: 200 kcal/100g, 10g P, 20g C, 5g F.
        // Registered 50g -> 100 kcal, 5g P, 10g C, 2.5g F.
        val originalEntry = MealEntry(
            id = 42L,
            date = "2026-10-01",
            meal = "LUNCH",
            productId = 99L,
            productName = "Yogur Especial",
            grams = 50.0,
            kcal = 100.0,
            protein = 5.0,
            carbs = 10.0,
            fat = 2.5,
            loggedAt = 123456L,
        )

        // Even if the product in DB is subsequently changed to 500 kcal/100g, 40g P, etc.,
        // scaling this entry to 100g must yield 200 kcal (factor 2.0 based on entry values).
        val scaledTo100g = MealPortionCalculator.rescaleEntry(originalEntry, newGrams = 100.0)

        assertEquals(100.0, scaledTo100g.grams, 0.001)
        assertEquals(200.0, scaledTo100g.kcal, 0.001)
        assertEquals(10.0, scaledTo100g.protein, 0.001)
        assertEquals(20.0, scaledTo100g.carbs, 0.001)
        assertEquals(5.0, scaledTo100g.fat, 0.001)
        assertEquals(originalEntry.id, scaledTo100g.id)
        assertEquals(originalEntry.loggedAt, scaledTo100g.loggedAt)
    }
}
