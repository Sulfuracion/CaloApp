package com.controlcal.core.nutrition

import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.NutrientTotals
import com.controlcal.core.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionCalculatorTest {

    private val sampleProduct = Product(
        id = 1L,
        name = "Pollo a la plancha",
        kcal100g = 165.0,
        protein100g = 31.0,
        carbs100g = 0.0,
        fat100g = 3.6,
        unitName = "filete",
        unitG = 120.0,
    )

    // -------------------------------------------------------------
    // CASO OBLIGATORIO 1: Día vacío
    // -------------------------------------------------------------
    @Test
    fun emptyDayReturnsZeroTotals() {
        val emptyEntries = emptyList<MealEntry>()
        val totals = NutritionCalculator.calculateTotals(emptyEntries)

        assertEquals(0.0, totals.kcal, 0.001)
        assertEquals(0.0, totals.protein, 0.001)
        assertEquals(0.0, totals.carbs, 0.001)
        assertEquals(0.0, totals.fat, 0.001)

        val dayTotals = NutritionCalculator.calculateDayTotals("2026-10-06", emptyEntries)
        assertEquals(0.0, dayTotals.totals.kcal, 0.001)
        assertEquals(4, dayTotals.byMeal.size)
        assertEquals(0.0, dayTotals.byMeal[DefaultMealIds.LUNCH]?.totals?.kcal ?: -1.0, 0.001)
        assertEquals(0.0, dayTotals.byMeal[DefaultMealIds.BREAKFAST]?.totals?.protein ?: -1.0, 0.001)
    }

    // -------------------------------------------------------------
    // CASO OBLIGATORIO 2: Entradas negativas de modo tara
    // -------------------------------------------------------------
    @Test
    fun negativeTareEntryCalculatesNegativeMacrosProportionally() {
        // Porción servida: 200 g de pollo
        val servedMacros = NutritionCalculator.calculatePortionMacros(sampleProduct, 200.0)
        assertEquals(330.0, servedMacros.kcal, 0.001)
        assertEquals(62.0, servedMacros.protein, 0.001)
        assertEquals(7.2, servedMacros.fat, 0.001)

        // Modo tara: se restan 50 g de sobras (-50 g)
        val tareMacros = NutritionCalculator.calculatePortionMacros(sampleProduct, -50.0)
        assertEquals(-82.5, tareMacros.kcal, 0.001)
        assertEquals(-15.5, tareMacros.protein, 0.001)
        assertEquals(-1.8, tareMacros.fat, 0.001)

        val entries = listOf(
            createEntry(meal = DefaultMealIds.LUNCH, grams = 200.0, macros = servedMacros),
            createEntry(meal = DefaultMealIds.LUNCH, grams = -50.0, macros = tareMacros),
        )

        val lunchTotals = NutritionCalculator.calculateTotals(entries)
        assertEquals(247.5, lunchTotals.kcal, 0.001) // 330 - 82.5
        assertEquals(46.5, lunchTotals.protein, 0.001) // 62 - 15.5
        assertEquals(5.4, lunchTotals.fat, 0.001)     // 7.2 - 1.8
    }

    // -------------------------------------------------------------
    // CASO OBLIGATORIO 3: Total que nunca baja de cero
    // -------------------------------------------------------------
    @Test
    fun totalsNeverDropBelowZeroEvenWithExcessNegativeEntries() {
        // Si hay una entrada negativa huérfana o mayor que lo consumido:
        // ej. -300 g cuando solo se sirvieron 100 g
        val served = createEntry(meal = DefaultMealIds.DINNER, grams = 100.0, macros = NutrientTotals(165.0, 31.0, 0.0, 3.6))
        val excessTare = createEntry(meal = DefaultMealIds.DINNER, grams = -300.0, macros = NutrientTotals(-495.0, -93.0, 0.0, -10.8))

        val totals = NutritionCalculator.calculateTotals(listOf(served, excessTare))

        // La suma bruta daría -330 kcal, pero la regla de negocio exige truncar a 0.0
        assertEquals(0.0, totals.kcal, 0.001)
        assertEquals(0.0, totals.protein, 0.001)
        assertEquals(0.0, totals.carbs, 0.001)
        assertEquals(0.0, totals.fat, 0.001)

        val dayTotals = NutritionCalculator.calculateDayTotals("2026-10-06", listOf(served, excessTare))
        assertEquals(0.0, dayTotals.totals.kcal, 0.001)
        assertEquals(0.0, dayTotals.byMeal[DefaultMealIds.DINNER]?.totals?.kcal ?: -1.0, 0.001)
    }

    // -------------------------------------------------------------
    // CASO OBLIGATORIO 4: Objetivo superado y restante
    // -------------------------------------------------------------
    @Test
    fun targetExceededYieldsZeroRemainingAndPositiveSurplus() {
        val targets = MacroTargets(
            kcal = 2000.0,
            protein = 150.0,
            carbs = 200.0,
            fat = 70.0,
        )

        // Totales que superan kcal y proteína, pero faltan carbohidratos y grasa
        val currentTotals = NutrientTotals(
            kcal = 2200.0,
            protein = 160.0,
            carbs = 180.0,
            fat = 60.0,
        )

        val remaining = NutritionCalculator.calculateRemaining(currentTotals, targets)

        // Kcal y proteína superadas: remaining = 0.0, surplus > 0.0
        assertEquals(0.0, remaining.remainingKcal, 0.001)
        assertEquals(200.0, remaining.surplusKcal, 0.001)

        assertEquals(0.0, remaining.remainingProtein, 0.001)
        assertEquals(10.0, remaining.surplusProtein, 0.001)

        // Carbohidratos y grasa no superados: remaining > 0.0, surplus = 0.0
        assertEquals(20.0, remaining.remainingCarbs, 0.001)
        assertEquals(0.0, remaining.surplusCarbs, 0.001)

        assertEquals(10.0, remaining.remainingFat, 0.001)
        assertEquals(0.0, remaining.surplusFat, 0.001)
    }

    @Test
    fun targetPartiallyMetLeavesPositiveRemainingAndZeroSurplus() {
        val targets = MacroTargets(
            kcal = 2000.0,
            protein = 150.0,
            carbs = 200.0,
            fat = 70.0,
        )
        val currentTotals = NutrientTotals(
            kcal = 1500.0,
            protein = 100.0,
            carbs = 150.0,
            fat = 50.0,
        )

        val remaining = NutritionCalculator.calculateRemaining(currentTotals, targets)

        assertEquals(500.0, remaining.remainingKcal, 0.001)
        assertEquals(0.0, remaining.surplusKcal, 0.001)
        assertEquals(50.0, remaining.remainingProtein, 0.001)
        assertEquals(0.0, remaining.surplusProtein, 0.001)
        assertEquals(50.0, remaining.remainingCarbs, 0.001)
        assertEquals(0.0, remaining.surplusCarbs, 0.001)
        assertEquals(20.0, remaining.remainingFat, 0.001)
        assertEquals(0.0, remaining.surplusFat, 0.001)
    }

    @Test
    fun unitConversionsWorkAccurately() {
        val grams = NutritionCalculator.unitsToGrams(units = 2.5, unitG = 120.0)
        assertEquals(300.0, grams, 0.001)

        val units = NutritionCalculator.gramsToUnits(grams = 300.0, unitG = 120.0)
        assertEquals(2.5, units, 0.001)

        // División entre cero segura
        val zeroUnitG = NutritionCalculator.gramsToUnits(grams = 100.0, unitG = 0.0)
        assertEquals(0.0, zeroUnitG, 0.001)
    }

    @Test
    fun dayWithNormalTareAndQuickCaloriesEntriesCalculatesTotalsAndCountsInIntake() {
        val normalEntry = MealEntry(
            id = 1L,
            date = "2026-10-15",
            meal = DefaultMealIds.LUNCH,
            productId = 10L,
            productName = "Pechuga de pollo",
            grams = 200.0,
            kcal = 300.0,
            protein = 40.0,
            carbs = 0.0,
            fat = 5.0,
            loggedAt = 1000L,
        )
        val tareEntry = MealEntry(
            id = 2L,
            date = "2026-10-15",
            meal = DefaultMealIds.LUNCH,
            productId = 10L,
            productName = "Pechuga de pollo",
            grams = -50.0,
            kcal = -75.0,
            protein = -10.0,
            carbs = 0.0,
            fat = -1.25,
            loggedAt = 2000L,
        )
        val quickCaloriesEntry = MealEntry(
            id = 3L,
            date = "2026-10-15",
            meal = DefaultMealIds.LUNCH,
            productId = null,
            productName = "Calorías rápidas",
            grams = 0.0,
            kcal = 150.0,
            protein = 8.0,
            carbs = 18.0,
            fat = 4.0,
            loggedAt = 3000L,
        )

        val dayEntries = listOf(normalEntry, tareEntry, quickCaloriesEntry)
        val dayTotals = NutritionCalculator.calculateDayTotals("2026-10-15", dayEntries)

        // All 3 entries appear
        val lunchEntries = dayTotals.byMeal[DefaultMealIds.LUNCH]?.entries.orEmpty()
        assertEquals(3, lunchEntries.size)
        assertTrue(lunchEntries.contains(normalEntry))
        assertTrue(lunchEntries.contains(tareEntry))
        assertTrue(lunchEntries.contains(quickCaloriesEntry))

        // Totals count all 3: 300 - 75 + 150 = 375 kcal
        assertEquals(375.0, dayTotals.totals.kcal, 0.001)
        assertEquals(38.0, dayTotals.totals.protein, 0.001)
        assertEquals(18.0, dayTotals.totals.carbs, 0.001)
        assertEquals(7.75, dayTotals.totals.fat, 0.001)

        // Verifies intake representation for TDEE
        val dailyIntake = com.controlcal.core.tdee.DailyIntake(
            date = "2026-10-15",
            kcal = dayTotals.totals.kcal,
            isComplete = dayEntries.isNotEmpty(),
        )
        assertEquals(375.0, dailyIntake.kcal, 0.001)
        assertTrue(dailyIntake.isComplete)
    }

    private fun createEntry(meal: String, grams: Double, macros: NutrientTotals): MealEntry =
        MealEntry(
            id = 0L,
            date = "2026-10-06",
            meal = meal,
            productId = sampleProduct.id,
            productName = sampleProduct.name,
            grams = grams,
            kcal = macros.kcal,
            protein = macros.protein,
            carbs = macros.carbs,
            fat = macros.fat,
            loggedAt = 1000L,
        )
}
