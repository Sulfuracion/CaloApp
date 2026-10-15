package com.controlcal.core.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodPortionFormatterTest {

    @Test
    fun formatGramsWithoutUnit() {
        assertEquals("150 g", FoodPortionFormatter.formatQuantity(150.0))
        assertEquals("12,5 g", FoodPortionFormatter.formatQuantity(12.5))
        assertEquals("0 g", FoodPortionFormatter.formatQuantity(0.0))
    }

    @Test
    fun formatDiscreteUnitsWhenUnitGIsProvided() {
        // 2 eggs (110 g) when 1 egg = 55 g
        val formatted = FoodPortionFormatter.formatQuantity(
            grams = 110.0,
            unitG = 55.0,
            unitName = "huevo"
        )
        assertEquals("2 huevo (110 g)", formatted)
    }

    @Test
    fun formatDiscreteUnitsWithDecimalUnits() {
        // 1.5 bananas (180 g) when 1 banana = 120 g
        val formatted = FoodPortionFormatter.formatQuantity(
            grams = 180.0,
            unitG = 120.0,
            unitName = "plátano"
        )
        assertEquals("1,5 plátano (180 g)", formatted)
    }

    @Test
    fun formatDiscreteUnitsDefaultsToUdWhenUnitNameIsNullOrBlank() {
        val formatted = FoodPortionFormatter.formatQuantity(
            grams = 100.0,
            unitG = 50.0,
            unitName = "  "
        )
        assertEquals("2 ud (100 g)", formatted)
    }

    @Test
    fun formatNegativeGramsInTareModeWithoutUnit() {
        val formatted = FoodPortionFormatter.formatQuantity(
            grams = -50.0,
            unitG = null
        )
        assertEquals("-50 g", formatted)
    }

    @Test
    fun formatNegativeGramsInTareModeWithUnit() {
        val formatted = FoodPortionFormatter.formatQuantity(
            grams = -55.0,
            unitG = 55.0,
            unitName = "huevo"
        )
        assertEquals("-1 huevo (-55 g)", formatted)
    }

    @Test
    fun formatWhenUnitGIsZeroOrNegativeFallsBackToStandardGrams() {
        assertEquals("100 g", FoodPortionFormatter.formatQuantity(100.0, unitG = 0.0, unitName = "pieza"))
        assertEquals("100 g", FoodPortionFormatter.formatQuantity(100.0, unitG = -10.0, unitName = "pieza"))
    }
}
