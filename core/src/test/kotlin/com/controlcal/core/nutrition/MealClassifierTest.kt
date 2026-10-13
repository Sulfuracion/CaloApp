package com.controlcal.core.nutrition

import com.controlcal.core.model.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealClassifierTest {

    @Test
    fun suggestMealForHour_breakfastHours() {
        for (h in 6..11) {
            assertEquals(Meal.BREAKFAST, MealClassifier.suggestMealForHour(h))
        }
    }

    @Test
    fun suggestMealForHour_lunchHours() {
        for (h in 12..16) {
            assertEquals(Meal.LUNCH, MealClassifier.suggestMealForHour(h))
        }
    }

    @Test
    fun suggestMealForHour_snackHours() {
        for (h in 17..20) {
            assertEquals(Meal.SNACK, MealClassifier.suggestMealForHour(h))
        }
    }

    @Test
    fun suggestMealForHour_dinnerHours() {
        val dinnerHours = listOf(21, 22, 23, 0, 1, 2, 3, 4, 5)
        for (h in dinnerHours) {
            assertEquals(Meal.DINNER, MealClassifier.suggestMealForHour(h))
        }
    }
}

class NumberFormatHelperTest {

    @Test
    fun formatDecimal_nullReturnsEmpty() {
        assertEquals("", NumberFormatHelper.formatDecimal(null))
    }

    @Test
    fun formatDecimal_wholeNumbersHaveNoDecimals() {
        assertEquals("0", NumberFormatHelper.formatDecimal(0.0))
        assertEquals("100", NumberFormatHelper.formatDecimal(100.0))
        assertEquals("250", NumberFormatHelper.formatDecimal(250.0))
    }

    @Test
    fun formatDecimal_usesCommaForDecimals() {
        assertEquals("12,5", NumberFormatHelper.formatDecimal(12.5))
        assertEquals("0,75", NumberFormatHelper.formatDecimal(0.75))
        assertEquals("1,23", NumberFormatHelper.formatDecimal(1.23))
    }

    @Test
    fun parseDecimal_handlesBothCommaAndDot() {
        assertEquals(12.5, NumberFormatHelper.parseDecimal("12,5")!!, 0.001)
        assertEquals(12.5, NumberFormatHelper.parseDecimal("12.5")!!, 0.001)
        assertEquals(100.0, NumberFormatHelper.parseDecimal("100")!!, 0.001)
        assertEquals(0.0, NumberFormatHelper.parseDecimal("0,0")!!, 0.001)
    }

    @Test
    fun parseDecimal_invalidOrBlankReturnsNull() {
        assertNull(NumberFormatHelper.parseDecimal(""))
        assertNull(NumberFormatHelper.parseDecimal("   "))
        assertNull(NumberFormatHelper.parseDecimal("abc"))
        assertNull(NumberFormatHelper.parseDecimal("12.3.4"))
    }
}
