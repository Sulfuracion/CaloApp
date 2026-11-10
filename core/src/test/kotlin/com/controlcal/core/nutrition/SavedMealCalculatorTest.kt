package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.model.SavedMeal
import com.controlcal.core.model.SavedMealItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedMealCalculatorTest {

    private val spaghetti = Product(
        id = 1L,
        name = "Espaguetis",
        kcal100g = 350.0,
        protein100g = 12.0,
        carbs100g = 72.0,
        fat100g = 1.5,
        isLiquid = false,
        source = ProductSource.MANUAL,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private val sausage = Product(
        id = 2L,
        name = "Salchicha",
        kcal100g = 280.0,
        protein100g = 14.0,
        carbs100g = 2.0,
        fat100g = 24.0,
        isLiquid = false,
        source = ProductSource.MANUAL,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private val cheese = Product(
        id = 3L,
        name = "Queso rallado",
        kcal100g = 390.0,
        protein100g = 25.0,
        carbs100g = 1.5,
        fat100g = 32.0,
        isLiquid = false,
        archived = true,
        source = ProductSource.MANUAL,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    @Test
    fun createFromEntries_aggregatesMultipleEntriesAndSubtractsTara() {
        val entries = listOf(
            // Spaghetti: 100g + 50g - 30g (tara) = 120g net
            MealEntry(id = 1L, date = "2026-11-10", meal = "LUNCH", productId = 1L, productName = "Espaguetis", grams = 100.0, kcal = 350.0, protein = 12.0, carbs = 72.0, fat = 1.5, loggedAt = 1000L),
            MealEntry(id = 2L, date = "2026-11-10", meal = "LUNCH", productId = 1L, productName = "Espaguetis", grams = 50.0, kcal = 175.0, protein = 6.0, carbs = 36.0, fat = 0.75, loggedAt = 1010L),
            MealEntry(id = 3L, date = "2026-11-10", meal = "LUNCH", productId = 1L, productName = "Espaguetis", grams = -30.0, kcal = -105.0, protein = -3.6, carbs = -21.6, fat = -0.45, loggedAt = 1020L),
            // Sausage: 80g
            MealEntry(id = 4L, date = "2026-11-10", meal = "LUNCH", productId = 2L, productName = "Salchicha", grams = 80.0, kcal = 224.0, protein = 11.2, carbs = 1.6, fat = 19.2, loggedAt = 1030L),
            // Cheese: 40g - 40g (tara) = 0g -> discarded
            MealEntry(id = 5L, date = "2026-11-10", meal = "LUNCH", productId = 3L, productName = "Queso", grams = 40.0, kcal = 156.0, protein = 10.0, carbs = 0.6, fat = 12.8, loggedAt = 1040L),
            MealEntry(id = 6L, date = "2026-11-10", meal = "LUNCH", productId = 3L, productName = "Queso", grams = -40.0, kcal = -156.0, protein = -10.0, carbs = -0.6, fat = -12.8, loggedAt = 1050L),
            // Standalone negative entry: discarded
            MealEntry(id = 7L, date = "2026-11-10", meal = "LUNCH", productId = 99L, productName = "Pan", grams = -50.0, kcal = -130.0, protein = -4.0, carbs = -25.0, fat = -1.0, loggedAt = 1060L),
            // Quick calories entry: preserved
            MealEntry(id = 8L, date = "2026-11-10", meal = "LUNCH", productId = null, productName = "Café con leche", grams = 0.0, kcal = 60.0, protein = 3.0, carbs = 5.0, fat = 2.0, loggedAt = 1070L),
        )

        val savedMeal = SavedMealCalculator.createFromEntries("Espaguetis con salchicha", entries, currentTime = 5000L)
        assertNotNull(savedMeal)
        assertEquals("Espaguetis con salchicha", savedMeal!!.name)
        assertEquals(3, savedMeal.items.size)

        val item1 = savedMeal.items.find { it.productId == 1L }
        assertNotNull(item1)
        assertEquals(120.0, item1!!.grams, 0.001)

        val item2 = savedMeal.items.find { it.productId == 2L }
        assertNotNull(item2)
        assertEquals(80.0, item2!!.grams, 0.001)

        val item3 = savedMeal.items.find { it.productId == null }
        assertNotNull(item3)
        assertEquals("Café con leche", item3!!.productName)
        assertEquals(60.0, item3.kcal!!, 0.001)
    }

    @Test
    fun createFromEntries_returnsNullIfAllItemsCancelledOrEmpty() {
        val entries = listOf(
            MealEntry(id = 1L, date = "2026-11-10", meal = "LUNCH", productId = 1L, productName = "Arroz", grams = 100.0, kcal = 350.0, protein = 7.0, carbs = 78.0, fat = 1.0, loggedAt = 1000L),
            MealEntry(id = 2L, date = "2026-11-10", meal = "LUNCH", productId = 1L, productName = "Arroz", grams = -100.0, kcal = -350.0, protein = -7.0, carbs = -78.0, fat = -1.0, loggedAt = 1010L),
        )

        assertNull(SavedMealCalculator.createFromEntries("Comida vacía", entries))
        assertNull(SavedMealCalculator.createFromEntries("", entries))
        assertNull(SavedMealCalculator.createFromEntries("   ", entries))
        assertNull(SavedMealCalculator.createFromEntries("Valida", emptyList()))
    }

    @Test
    fun resolveMeal_calculatesDynamicMacrosAndDetectsArchivedProducts() {
        val meal = SavedMeal(
            id = 10L,
            name = "Pasta plato",
            createdAt = 1000L,
            updatedAt = 1000L,
            items = listOf(
                SavedMealItem(id = 1L, savedMealId = 10L, position = 0, productId = 1L, productName = "Espaguetis", grams = 100.0),
                SavedMealItem(id = 2L, savedMealId = 10L, position = 1, productId = 3L, productName = "Queso rallado", grams = 20.0),
                SavedMealItem(id = 3L, savedMealId = 10L, position = 2, productId = null, productName = "Calorías rápidas", grams = 0.0, kcal = 50.0, protein = 2.0, carbs = 8.0, fat = 1.0),
            )
        )

        val productsById = mapOf(1L to spaghetti, 3L to cheese)

        val resolved = SavedMealCalculator.resolveMeal(meal, productsById, multiplier = 1.0)
        assertEquals(3, resolved.resolvedItems.size)
        assertTrue(resolved.hasArchivedProducts) // Cheese is archived

        // Item 1: 100g spaghetti -> 350 kcal, 12 P, 72 C, 1.5 F
        val item1 = resolved.resolvedItems[0]
        assertEquals(350.0, item1.resolvedKcal, 0.001)
        assertFalse(item1.isArchived)

        // Item 2: 20g cheese (390 kcal/100g) -> 78 kcal, 5 P, 0.3 C, 6.4 F
        val item2 = resolved.resolvedItems[1]
        assertEquals(78.0, item2.resolvedKcal, 0.001)
        assertTrue(item2.isArchived)

        // Item 3: Quick calories frozen -> 50 kcal, 2 P, 8 C, 1 F
        val item3 = resolved.resolvedItems[2]
        assertEquals(50.0, item3.resolvedKcal, 0.001)
        assertFalse(item3.isArchived)

        // Totals: 350 + 78 + 50 = 478 kcal
        assertEquals(478.0, resolved.totalKcal, 0.001)
        assertEquals(19.0, resolved.totalProtein, 0.001)
        assertEquals(80.3, resolved.totalCarbs, 0.001)
        assertEquals(8.9, resolved.totalFat, 0.001)
    }

    @Test
    fun resolveMeal_withMultipliersScalesGramsAndMacros() {
        val meal = SavedMeal(
            id = 10L,
            name = "Salchichas",
            createdAt = 1000L,
            updatedAt = 1000L,
            items = listOf(
                SavedMealItem(id = 1L, savedMealId = 10L, position = 0, productId = 2L, productName = "Salchicha", grams = 100.0),
                SavedMealItem(id = 2L, savedMealId = 10L, position = 1, productId = null, productName = "Pan rápido", grams = 0.0, kcal = 100.0, protein = 3.0, carbs = 20.0, fat = 1.0),
            )
        )

        val productsById = mapOf(2L to sausage)

        // Multiplier 1.5
        val resolved15 = SavedMealCalculator.resolveMeal(meal, productsById, multiplier = 1.5)
        assertEquals(150.0, resolved15.resolvedItems[0].resolvedGrams, 0.001)
        // Sausage 150g -> 280 * 1.5 = 420 kcal
        assertEquals(420.0, resolved15.resolvedItems[0].resolvedKcal, 0.001)
        // Quick cal: 100 * 1.5 = 150 kcal
        assertEquals(150.0, resolved15.resolvedItems[1].resolvedKcal, 0.001)
        assertEquals(570.0, resolved15.totalKcal, 0.001)

        // Multiplier 0.5
        val resolved05 = SavedMealCalculator.resolveMeal(meal, productsById, multiplier = 0.5)
        assertEquals(50.0, resolved05.resolvedItems[0].resolvedGrams, 0.001)
        assertEquals(140.0, resolved05.resolvedItems[0].resolvedKcal, 0.001)
        assertEquals(50.0, resolved05.resolvedItems[1].resolvedKcal, 0.001)
        assertEquals(190.0, resolved05.totalKcal, 0.001)
    }

    @Test
    fun buildMealEntries_generatesValidEntriesForDiary() {
        val meal = SavedMeal(
            id = 10L,
            name = "Espaguetis con salchicha",
            createdAt = 1000L,
            updatedAt = 1000L,
            items = listOf(
                SavedMealItem(id = 1L, savedMealId = 10L, position = 0, productId = 1L, productName = "Espaguetis", grams = 100.0),
                SavedMealItem(id = 2L, savedMealId = 10L, position = 1, productId = null, productName = "Salsa rápida", grams = 0.0, kcal = 80.0, protein = 1.0, carbs = 10.0, fat = 4.0),
            )
        )

        val productsById = mapOf(1L to spaghetti)

        val entries = SavedMealCalculator.buildMealEntries(
            meal = meal,
            productsById = productsById,
            multiplier = 2.0,
            date = "2026-11-10",
            mealId = "DINNER",
            loggedAt = 9999L,
        )

        assertEquals(2, entries.size)

        val entry1 = entries[0]
        assertEquals("2026-11-10", entry1.date)
        assertEquals("DINNER", entry1.meal)
        assertEquals(1L, entry1.productId)
        assertEquals(200.0, entry1.grams, 0.001)
        assertEquals(700.0, entry1.kcal, 0.001)
        assertEquals(24.0, entry1.protein, 0.001)
        assertEquals(9999L, entry1.loggedAt)

        val entry2 = entries[1]
        assertNull(entry2.productId)
        assertEquals("Salsa rápida", entry2.productName)
        assertEquals(0.0, entry2.grams, 0.001)
        assertEquals(160.0, entry2.kcal, 0.001)
        assertEquals(2.0, entry2.protein, 0.001)
        assertEquals(20.0, entry2.carbs, 0.001)
        assertEquals(8.0, entry2.fat, 0.001)
        assertEquals(9999L, entry2.loggedAt)
    }
}
