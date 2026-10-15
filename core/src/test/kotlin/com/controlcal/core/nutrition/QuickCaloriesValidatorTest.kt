package com.controlcal.core.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCaloriesValidatorTest {

    @Test
    fun nullOrNonPositiveKcalIsInvalid() {
        val nullResult = QuickCaloriesValidator.validate(null)
        assertFalse(nullResult.isValid)
        assertEquals("Las calorías son obligatorias y deben ser mayores que 0", nullResult.kcalError)

        val zeroResult = QuickCaloriesValidator.validate(0.0)
        assertFalse(zeroResult.isValid)

        val negativeResult = QuickCaloriesValidator.validate(-50.0)
        assertFalse(negativeResult.isValid)
    }

    @Test
    fun positiveKcalWithoutMacrosIsValidAndNoWarning() {
        val result = QuickCaloriesValidator.validate(250.0)
        assertTrue(result.isValid)
        assertFalse(result.hasAtwaterWarning)
        assertEquals(0.0, result.calculatedAtwaterKcal, 0.001)
    }

    @Test
    fun allThreeMacrosWithin15PercentDoesNotWarn() {
        // 20g P (80 kcal) + 30g C (120 kcal) + 10g F (90 kcal) = 290 kcal
        // 290 vs 300 kcal: diff is 10 / 300 = 3.3% <= 15%
        val result = QuickCaloriesValidator.validate(
            kcal = 300.0,
            protein = 20.0,
            carbs = 30.0,
            fat = 10.0,
            hasProteinInput = true,
            hasCarbsInput = true,
            hasFatInput = true,
        )
        assertTrue(result.isValid)
        assertFalse(result.hasAtwaterWarning)
        assertEquals(290.0, result.calculatedAtwaterKcal, 0.001)
    }

    @Test
    fun allThreeMacrosDeviatingMoreThan15PercentWarnsWithoutBlocking() {
        // 10g P (40) + 10g C (40) + 2g F (18) = 98 kcal
        // 98 vs 200 kcal: diff is 102 / 200 = 51% > 15%
        val result = QuickCaloriesValidator.validate(
            kcal = 200.0,
            protein = 10.0,
            carbs = 10.0,
            fat = 2.0,
            hasProteinInput = true,
            hasCarbsInput = true,
            hasFatInput = true,
        )
        assertTrue(result.isValid) // Warn without blocking!
        assertTrue(result.hasAtwaterWarning)
        assertEquals(98.0, result.calculatedAtwaterKcal, 0.001)
    }

    @Test
    fun partialMacrosBelowTotalDoNotWarnEvenIfSignificantlyLower() {
        // Only entered protein: 20g P = 80 kcal, while total is 500 kcal
        // Since macros are partial, it's expected to be lower than total
        val result = QuickCaloriesValidator.validate(
            kcal = 500.0,
            protein = 20.0,
            hasProteinInput = true,
            hasCarbsInput = false,
            hasFatInput = false,
        )
        assertTrue(result.isValid)
        assertFalse(result.hasAtwaterWarning)
    }

    @Test
    fun partialMacrosExceedingTotalByMoreThan15PercentWarns() {
        // Total = 100 kcal. User enters 30g fat = 270 kcal.
        // 270 exceeds 100 by 170%, which is > 15%
        val result = QuickCaloriesValidator.validate(
            kcal = 100.0,
            fat = 30.0,
            hasProteinInput = false,
            hasCarbsInput = false,
            hasFatInput = true,
        )
        assertTrue(result.isValid)
        assertTrue(result.hasAtwaterWarning)
    }

    @Test
    fun partialMacrosExceedingTotalByLessThan15PercentDoesNotWarn() {
        // Total = 100 kcal. User enters 26g carbs = 104 kcal.
        // 104 exceeds 100 by 4% <= 15%
        val result = QuickCaloriesValidator.validate(
            kcal = 100.0,
            carbs = 26.0,
            hasProteinInput = false,
            hasCarbsInput = true,
            hasFatInput = false,
        )
        assertTrue(result.isValid)
        assertFalse(result.hasAtwaterWarning)
    }

    @Test
    fun sanitizeDescriptionDefaultsToCaloriasRapidas() {
        assertEquals("Calorías rápidas", QuickCaloriesValidator.sanitizeDescription(null))
        assertEquals("Calorías rápidas", QuickCaloriesValidator.sanitizeDescription(""))
        assertEquals("Calorías rápidas", QuickCaloriesValidator.sanitizeDescription("   "))
        assertEquals("Café con leche", QuickCaloriesValidator.sanitizeDescription("Café con leche"))
    }
}
