package com.controlcal.core.ocr

import com.controlcal.core.ocr.model.OcrParsedNutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelValidatorTest {

    @Test
    fun validNutrientsPassValidation() {
        val nutrients = OcrParsedNutrients(
            kcal = 250.0,
            protein = 10.0,
            carbs = 30.0,
            fat = 10.0,
            sugars = 5.0,
            satFat = 2.0,
            fiber = 3.0,
            salt = 1.0
        )
        // 4*10 + 4*30 + 9*10 + 2*3 = 40 + 120 + 90 + 6 = 256. Diff = |256 - 250| = 6 <= 37.5 (15%)
        val result = LabelValidator.validate(nutrients)
        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
        assertTrue(result.invalidFields.isEmpty())
    }

    @Test
    fun negativeValuesFailValidation() {
        val nutrients = OcrParsedNutrients(
            kcal = -10.0,
            protein = -1.0,
            carbs = 10.0,
            fat = 5.0
        )
        val result = LabelValidator.validate(nutrients)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.rule == ValidationRule.NON_NEGATIVE && it.field == "kcal" })
        assertTrue(result.issues.any { it.rule == ValidationRule.NON_NEGATIVE && it.field == "protein" })
        assertTrue(result.invalidFields.contains("kcal"))
        assertTrue(result.invalidFields.contains("protein"))
    }

    @Test
    fun kcalExceeding900FailsValidation() {
        val nutrients = OcrParsedNutrients(
            kcal = 950.0,
            protein = 0.0,
            carbs = 0.0,
            fat = 100.0
        )
        val result = LabelValidator.validate(nutrients)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.rule == ValidationRule.KCAL_LEQ_900 })
        assertTrue(result.invalidFields.contains("kcal"))
    }

    @Test
    fun nutrientExceeding100gPer100gFailsValidation() {
        val nutrients = OcrParsedNutrients(
            kcal = 450.0,
            protein = 105.0,
            carbs = 10.0,
            fat = 2.0
        )
        val result = LabelValidator.validate(nutrients)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.rule == ValidationRule.VALUE_LEQ_100 && it.field == "protein" })
        assertTrue(result.invalidFields.contains("protein"))
    }

    @Test
    fun sugarsExceedingCarbsFailsValidation() {
        val nutrients = OcrParsedNutrients(
            kcal = 100.0,
            protein = 5.0,
            carbs = 10.0,
            fat = 2.0,
            sugars = 15.0
        )
        val result = LabelValidator.validate(nutrients)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.rule == ValidationRule.SUGARS_LEQ_CARBS })
        assertTrue(result.invalidFields.contains("sugars"))
    }

    @Test
    fun satFatExceedingFatFailsValidation() {
        val nutrients = OcrParsedNutrients(
            kcal = 100.0,
            protein = 5.0,
            carbs = 10.0,
            fat = 2.0,
            satFat = 3.5
        )
        val result = LabelValidator.validate(nutrients)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.rule == ValidationRule.SAT_FAT_LEQ_FAT })
        assertTrue(result.invalidFields.contains("satFat"))
    }

    @Test
    fun caloricBalanceMismatchExceeding15PercentFails() {
        // Reported kcal: 100
        // Calculated kcal: 4*20 (80) + 4*20 (80) + 9*10 (90) = 250 kcal
        // Diff = 150 > 15 (15% of 100)
        val nutrients = OcrParsedNutrients(
            kcal = 100.0,
            protein = 20.0,
            carbs = 20.0,
            fat = 10.0
        )
        val result = LabelValidator.validate(nutrients)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.rule == ValidationRule.CALORIC_BALANCE })
        assertTrue(result.invalidFields.containsAll(listOf("kcal", "protein", "carbs", "fat")))
    }

    @Test
    fun fiberIncludedInCaloricBalance() {
        // Reported kcal: 100
        // P=10, C=10, F=0, Fiber=10
        // 4*10 + 4*10 + 9*0 + 2*10 = 40 + 40 + 20 = 100 kcal (exact match)
        val nutrients = OcrParsedNutrients(
            kcal = 100.0,
            protein = 10.0,
            carbs = 10.0,
            fat = 0.0,
            fiber = 10.0
        )
        val result = LabelValidator.validate(nutrients)
        assertTrue(result.isValid)
    }

    @Test
    fun validationIssuesExposeValuesForUiFormatting() {
        val nutrients = OcrParsedNutrients(
            kcal = 100.0,
            protein = 5.0,
            carbs = 10.2,
            fat = 2.0,
            sugars = 15.5,
            satFat = 3.5,
        )
        val result = LabelValidator.validate(nutrients)
        assertFalse(result.isValid)
        val sugarsIssue = result.issues.first { it.rule == ValidationRule.SUGARS_LEQ_CARBS }
        assertEquals(listOf(15.5, 10.2), sugarsIssue.values)
        val satFatIssue = result.issues.first { it.rule == ValidationRule.SAT_FAT_LEQ_FAT }
        assertEquals(listOf(3.5, 2.0), satFatIssue.values)
    }
}
