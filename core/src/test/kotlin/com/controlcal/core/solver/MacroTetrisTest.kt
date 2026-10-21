package com.controlcal.core.solver

import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.system.measureTimeMillis

class MacroTetrisTest {

    // -------------------------------------------------------------
    // Helper para crear productos de prueba
    // -------------------------------------------------------------
    private fun createProduct(
        id: Long,
        name: String,
        kcal100g: Double,
        protein100g: Double,
        carbs100g: Double,
        fat100g: Double,
        inPantry: Boolean = true,
        unitG: Double? = null,
        unitName: String? = null,
        minG: Double? = null,
        maxG: Double? = null,
        isLiquid: Boolean = false,
    ): Product = Product(
        id = id,
        name = name,
        kcal100g = kcal100g,
        protein100g = protein100g,
        carbs100g = carbs100g,
        fat100g = fat100g,
        inPantry = inPantry,
        unitG = unitG,
        unitName = unitName,
        minG = minG,
        maxG = maxG,
        isLiquid = isLiquid,
        source = ProductSource.MANUAL,
    )

    // =============================================================
    // CASOS LÍMITE DE PLAN.md 5.6 (Paso 7)
    // =============================================================

    @Test
    fun emptyPantryReturnsEmptySolutionWithSuggestions() {
        val target = MacroTargets(kcal = 500.0, protein = 40.0, carbs = 50.0, fat = 15.0)
        val emptyPantry = emptyList<Product>()

        val solution = FoodSelector.selectAndSolve(target, emptyPantry)

        assertTrue("La solución debe contener 0 ítems con despensa vacía", solution.items.isEmpty())
        assertEquals(0.0, solution.achievedMacros.kcal, 0.001)
        assertEquals(0.0, solution.achievedMacros.protein, 0.001)
        assertEquals(0.0, solution.achievedMacros.carbs, 0.001)
        assertEquals(0.0, solution.achievedMacros.fat, 0.001)

        // Error real reportado al 100%
        assertEquals(100.0, solution.error.percentKcal, 0.001)
        assertEquals(100.0, solution.error.percentProtein, 0.001)
        assertEquals(100.0, solution.error.percentCarbs, 0.001)
        assertEquals(100.0, solution.error.percentFat, 0.001)

        // Sugerencias generadas indicando falta de alimentos
        assertTrue("Debe incluir sugerencias sobre despensa o macros faltantes", solution.suggestions.isNotEmpty())
    }

    @Test
    fun zeroOrNegativeRemainingMacrosReturnsZeroGrams() {
        val targetZero = MacroTargets(kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0)
        val chicken = createProduct(1L, "Pechuga de pollo", 110.0, 23.0, 0.0, 1.5)
        val rice = createProduct(2L, "Arroz", 130.0, 2.7, 28.0, 0.3)
        val pantry = listOf(chicken, rice)

        val solutionZero = FoodSelector.selectAndSolve(targetZero, pantry)
        assertTrue("No debe asignar gramos si los macros restantes son 0", solutionZero.items.isEmpty())
        assertEquals(0.0, solutionZero.achievedMacros.kcal, 0.001)

        val targetNegative = MacroTargets(kcal = -50.0, protein = -10.0, carbs = -5.0, fat = -2.0)
        val solutionNegative = FoodSelector.selectAndSolve(targetNegative, pantry)
        assertTrue("No debe asignar gramos si los macros restantes son negativos", solutionNegative.items.isEmpty())
        assertEquals(0.0, solutionNegative.achievedMacros.kcal, 0.001)
    }

    @Test
    fun singleProductInPantryClampsToLimitsAndCalculatesRealError() {
        val target = MacroTargets(kcal = 500.0, protein = 80.0, carbs = 10.0, fat = 5.0)
        // Pollo: por defecto maxG = 300 g
        val chicken = createProduct(1L, "Pechuga de pollo", 110.0, 23.0, 0.0, 1.5)
        val pantry = listOf(chicken)

        val solution = FoodSelector.selectAndSolve(target, pantry)

        assertEquals("Debe seleccionar el único producto", 1, solution.items.size)
        val item = solution.items.first()
        assertEquals(chicken.id, item.product.id)

        // No debe exceder el límite por defecto de 300 g
        assertTrue("Gramos <= 300g", item.roundedGrams <= 300.0)
        assertTrue("Gramos >= 0g", item.roundedGrams >= 0.0)
        assertEquals("Debe redondearse a múltiplo de 5g", 0.0, item.roundedGrams % 5.0, 0.001)

        // Debe calcular el error real
        val expectedAchievedProtein = (chicken.protein100g * item.roundedGrams) / 100.0
        assertEquals(expectedAchievedProtein, solution.achievedMacros.protein, 0.01)
        assertEquals(solution.achievedMacros.protein - target.protein, solution.error.diffProtein, 0.01)

        // Como no hay carbohidratos, el error de carbohidratos debe ser 100% y generar sugerencia
        assertEquals(100.0, solution.error.percentCarbs, 0.01)
        assertTrue(solution.suggestions.any { it.suggestionType == MacroSuggestionType.MISSING_CARBS })
    }

    @Test
    fun nearlyIdenticalProductsDoNotCrashOrDivideByZero() {
        val target = MacroTargets(kcal = 300.0, protein = 30.0, carbs = 20.0, fat = 5.0)
        // Dos marcas con macros casi idénticos
        val chickenA = createProduct(1L, "Pechuga Marca A", 110.0, 23.0, 0.0, 1.5)
        val chickenB = createProduct(2L, "Pechuga Marca B", 110.2, 22.9, 0.1, 1.4)
        val rice = createProduct(3L, "Arroz", 130.0, 2.7, 28.0, 0.3)
        val pantry = listOf(chickenA, chickenB, rice)

        val solution = FoodSelector.selectAndSolve(target, pantry)

        assertTrue("Debe encontrar una solución sin excepciones ni NaN", solution.items.isNotEmpty())
        for (item in solution.items) {
            assertTrue("Gramos deben ser un número finito válido", item.roundedGrams.isFinite())
            assertTrue("Gramos >= 0", item.roundedGrams >= 0.0)
        }
        assertTrue("Achieved kcal debe ser finito", solution.achievedMacros.kcal.isFinite())
        assertTrue("Error debe ser finito", solution.error.percentProtein.isFinite())
    }

    // =============================================================
    // 3 CASOS REALES PROPUESTOS (Ejemplos representativos)
    // =============================================================

    @Test
    fun realCase1_postWorkoutDinner_balancedSolutionWithinBounds() {
        // Macros restantes: 450 kcal, 40 g P, 35 g C, 10 g G
        val target = MacroTargets(kcal = 450.0, protein = 40.0, carbs = 35.0, fat = 10.0)

        val chicken = createProduct(1L, "Pechuga de pollo", 110.0, 23.0, 0.0, 1.5)
        val rice = createProduct(2L, "Arroz cocido", 130.0, 2.7, 28.0, 0.3)
        val oliveOil = createProduct(3L, "Aceite de oliva virgen extra", 884.0, 0.0, 0.0, 100.0)
        val egg = createProduct(4L, "Huevo mediano", 155.0, 13.0, 1.1, 11.0, unitG = 55.0, unitName = "huevo")
        val pantry = listOf(chicken, rice, oliveOil, egg)

        val solution = FoodSelector.selectAndSolve(target, pantry)

        assertTrue("Debe seleccionar entre 1 y 5 productos", solution.items.size in 1..5)

        // Comprobamos que el aceite nunca supera 30g por la regla de grasa > 80%
        val oilItem = solution.items.find { it.product.id == oliveOil.id }
        if (oilItem != null) {
            assertTrue("El aceite no debe superar 30g", oilItem.roundedGrams <= 30.0)
        }

        // Todos los gramos redondeados a múltiplos de 5g (o 55g para huevo)
        for (item in solution.items) {
            val unitG = item.product.unitG
            if (unitG != null) {
                assertEquals(0.0, item.roundedGrams % unitG, 0.001)
            } else {
                assertEquals(0.0, item.roundedGrams % 5.0, 0.001)
            }
        }

        // Precisión general razonable: la proteína debe quedar muy cerca del objetivo (peso 1.5)
        val proteinErrorPct = solution.error.percentProtein
        assertTrue("La proteína debe quedar a menos del 15% del objetivo (obtenido: $proteinErrorPct%)", proteinErrorPct < 15.0)
    }

    @Test
    fun realCase2_lowCarbRestDay_minimizesCarbsAndFillsFatProtein() {
        // Cena cetogénica / día descanso: 380 kcal, 32 g P, 6 g C, 25 g G
        val target = MacroTargets(kcal = 380.0, protein = 32.0, carbs = 6.0, fat = 25.0)

        val salmon = createProduct(1L, "Lomo de salmón", 208.0, 20.0, 0.0, 13.0)
        val avocado = createProduct(2L, "Aguacate", 160.0, 2.0, 9.0, 15.0)
        val quark0 = createProduct(3L, "Queso fresco batido 0%", 52.0, 9.0, 4.0, 0.1)
        val walnuts = createProduct(4L, "Nueces", 654.0, 15.0, 14.0, 65.0)
        val pantry = listOf(salmon, avocado, quark0, walnuts)

        val solution = FoodSelector.selectAndSolve(target, pantry)

        assertTrue("Debe seleccionar alimentos de la despensa", solution.items.isNotEmpty())
        assertTrue("Máximo 5 alimentos", solution.items.size <= 5)

        // Los carbohidratos deben mantenerse bajos
        assertTrue("Los carbohidratos conseguidos deben ser razonables (<= 15g)", solution.achievedMacros.carbs <= 15.0)
        assertTrue("Proteína conseguida >= 20g", solution.achievedMacros.protein >= 20.0)
        assertTrue("Grasa conseguida >= 15g", solution.achievedMacros.fat >= 15.0)
    }

    @Test
    fun realCase3_discreteUnitsRoundedStrictlyToMultiplesOfUnitG() {
        // Desayuno con huevos (55g/ud), avena y leche
        val target = MacroTargets(kcal = 330.0, protein = 22.0, carbs = 32.0, fat = 12.0)

        val egg = createProduct(1L, "Huevo", 155.0, 13.0, 1.1, 11.0, unitG = 55.0, unitName = "huevo")
        val oats = createProduct(2L, "Copos de avena", 370.0, 13.5, 58.7, 7.0)
        val milk = createProduct(3L, "Leche desnatada", 34.0, 3.4, 5.0, 0.1, isLiquid = true)
        val pantry = listOf(egg, oats, milk)

        val solution = FoodSelector.selectAndSolve(target, pantry)

        val eggItem = solution.items.find { it.product.id == egg.id }
        if (eggItem != null) {
            assertTrue("Huevo debe ser unitario", eggItem.isUnitBased)
            assertNotNull(eggItem.unitCount)
            assertEquals("Huevo debe ser múltiplo entero de 55g", 0.0, eggItem.roundedGrams % 55.0, 0.001)
            assertTrue("Cantidad de huevos >= 1", eggItem.unitCount!! >= 1.0)
        }

        val oatsItem = solution.items.find { it.product.id == oats.id }
        if (oatsItem != null) {
            assertEquals("Avena redondeada a múltiplos de 5g", 0.0, oatsItem.roundedGrams % 5.0, 0.001)
        }
    }

    // =============================================================
    // REGLA DE LÍMITES POR DEFECTO (Paso 2)
    // =============================================================

    @Test
    fun defaultBoundsEnforced_fatOver80PercentCappedAt30g_standardCappedAt300g() {
        val target = MacroTargets(kcal = 2000.0, protein = 200.0, carbs = 200.0, fat = 200.0)

        val pureFatOil = createProduct(1L, "Aceite", 884.0, 0.0, 0.0, 99.0) // Grasa > 80%
        val meat = createProduct(2L, "Carne magra", 120.0, 22.0, 0.0, 3.0)  // Grasa <= 80%
        val pantry = listOf(pureFatOil, meat)

        val solution = FoodSelector.selectAndSolve(target, pantry)

        val oilItem = solution.items.find { it.product.id == pureFatOil.id }
        if (oilItem != null) {
            assertTrue("Aceite con grasa > 80% nunca debe superar 30g", oilItem.roundedGrams <= 30.0)
        }

        val meatItem = solution.items.find { it.product.id == meat.id }
        if (meatItem != null) {
            assertTrue("Alimento sin límite explícito nunca debe superar 300g", meatItem.roundedGrams <= 300.0)
        }
    }

    // =============================================================
    // REGLA DE ERROR > 10% Y SUGERENCIAS (Paso 6)
    // =============================================================

    @Test
    fun macroShortfallOver10PercentGeneratesClearSuggestion() {
        // Pedimos 100g de proteína, pero la despensa solo tiene arroz (muy bajo en proteína)
        val target = MacroTargets(kcal = 600.0, protein = 100.0, carbs = 50.0, fat = 5.0)
        val rice = createProduct(1L, "Arroz", 130.0, 2.7, 28.0, 0.3)
        val pantry = listOf(rice)

        val solution = FoodSelector.selectAndSolve(target, pantry)

        assertTrue("El error de proteína debe ser mayor al 10%", solution.error.percentProtein > 10.0)
        val proteinSuggestion = solution.suggestions.find { it.suggestionType == MacroSuggestionType.MISSING_PROTEIN }
        assertNotNull("Debe sugerir que falta proteína", proteinSuggestion)
        assertTrue("El mensaje debe estar en español y mencionar fuentes proteicas", proteinSuggestion!!.message.contains("proteína", ignoreCase = true))
    }

    // =============================================================
    // TEST DE RENDIMIENTO: 30 PRODUCTOS EN < 100 MS (PLAN sección 7)
    // =============================================================

    @Test
    fun performanceTest_thirtyProductsSolvesInLessThan100Milliseconds() {
        val target = MacroTargets(kcal = 650.0, protein = 45.0, carbs = 60.0, fat = 18.0)

        // Generamos 30 productos con perfiles realistas variados
        val pantry30 = (1..30).map { i ->
            when (i % 6) {
                0 -> createProduct(i.toLong(), "Proteico $i", 110.0 + i, 22.0 + (i % 5), 0.0, 1.5)
                1 -> createProduct(i.toLong(), "Carbohidrato $i", 130.0 + i, 2.5, 28.0 + (i % 10), 0.5)
                2 -> createProduct(i.toLong(), "Graso $i", 800.0, 0.0, 0.0, 90.0)
                3 -> createProduct(i.toLong(), "Mixto $i", 250.0, 15.0, 15.0, 12.0)
                4 -> createProduct(i.toLong(), "Unitario $i", 150.0, 12.0, 2.0, 10.0, unitG = 50.0, unitName = "pieza")
                else -> createProduct(i.toLong(), "Verdura $i", 35.0, 2.0, 5.0, 0.2)
            }
        }

        // Warm-up inicial para JIT
        FoodSelector.selectAndSolve(target, pantry30.take(5))

        // Medición
        val elapsedMs = measureTimeMillis {
            val solution = FoodSelector.selectAndSolve(target, pantry30)
            assertTrue("Debe encontrar una solución", solution.items.isNotEmpty())
            assertTrue("Máximo 5 productos seleccionados", solution.items.size <= 5)
        }

        println("Performance Macro Tetris (30 productos): ${elapsedMs}ms")
        assertTrue("Macro Tetris con 30 productos debe tardar menos de 100 ms (tardó ${elapsedMs}ms)", elapsedMs < 100)
    }

    @Test
    fun filtersOutArchivedAndNotInPantryProductsAndRespectsExplicitMinMaxBounds() {
        val target = MacroTargets(kcal = 400.0, protein = 30.0, carbs = 40.0, fat = 10.0)
        val archivedProduct = createProduct(1L, "Archivado", 200.0, 20.0, 20.0, 5.0, inPantry = true).copy(archived = true)
        val notInPantry = createProduct(2L, "Fuera de despensa", 200.0, 20.0, 20.0, 5.0, inPantry = false)
        val boundedProduct = createProduct(3L, "Avena acotada", 370.0, 13.0, 60.0, 7.0, minG = 40.0, maxG = 60.0)

        val solution = FoodSelector.selectAndSolve(target, listOf(archivedProduct, notInPantry, boundedProduct))
        assertEquals(1, solution.items.size)
        val item = solution.items.single()
        assertEquals(3L, item.product.id)
        assertTrue(item.roundedGrams in 40.0..60.0)
    }

    @Test
    fun dayRemainingOverloadAndClassifyFoodWorkCorrectly() {
        val chicken = createProduct(1L, "Pollo", 110.0, 23.0, 0.0, 1.5)
        val rice = createProduct(2L, "Arroz", 130.0, 2.7, 28.0, 0.3)
        val oil = createProduct(3L, "Aceite", 884.0, 0.0, 0.0, 100.0)
        val balanced = createProduct(4L, "Plato mixto", 200.0, 15.0, 20.0, 6.5)

        assertEquals(FoodCategory.PROTEIN, FoodSelector.classifyFood(chicken))
        assertEquals(FoodCategory.CARBS, FoodSelector.classifyFood(rice))
        assertEquals(FoodCategory.FAT, FoodSelector.classifyFood(oil))
        assertEquals(FoodCategory.BALANCED, FoodSelector.classifyFood(balanced))

        val remaining = com.controlcal.core.model.DayRemaining(
            remainingKcal = 300.0,
            remainingProtein = 25.0,
            remainingCarbs = 30.0,
            remainingFat = 8.0,
            surplusKcal = 0.0,
        )
        val solution = FoodSelector.selectAndSolve(remaining, listOf(chicken, rice, oil))
        assertTrue(solution.items.isNotEmpty())
    }
}
