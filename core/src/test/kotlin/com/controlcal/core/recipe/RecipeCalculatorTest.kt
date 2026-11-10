package com.controlcal.core.recipe

import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.model.Recipe
import com.controlcal.core.model.RecipeIngredient
import com.controlcal.core.model.ResolvedRecipeIngredient
import com.controlcal.core.ocr.ValidationRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeCalculatorTest {

    private val chickenBreast = Product(
        id = 101L,
        name = "Pechuga de pollo",
        kcal100g = 120.0,
        protein100g = 22.5,
        carbs100g = 0.0,
        fat100g = 2.5,
        fiber100g = 0.0,
        sugars100g = 0.0,
        satFat100g = 0.7,
        salt100g = 0.1,
    )

    private val whiteRice = Product(
        id = 102L,
        name = "Arroz blanco",
        kcal100g = 350.0,
        protein100g = 7.0,
        carbs100g = 78.0,
        fat100g = 1.0,
        fiber100g = 1.5,
        sugars100g = 0.2,
        satFat100g = 0.3,
        salt100g = 0.01,
    )

    private val oliveOil = Product(
        id = 103L,
        name = "Aceite de oliva",
        kcal100g = 900.0,
        protein100g = 0.0,
        carbs100g = 0.0,
        fat100g = 100.0,
        fiber100g = null, // Incomplete secondary field
        sugars100g = 0.0,
        satFat100g = 14.0,
        salt100g = 0.0,
    )

    @Test
    fun `calculation without cooked weight uses raw weight sum as base`() {
        // 200g chicken (240 kcal, 45g P, 0g C, 5g F)
        // 100g rice (350 kcal, 7g P, 78g C, 1g F)
        // Total raw = 300g, Total kcal = 590, P = 52, C = 78, F = 6
        val ingredients = listOf(
            ResolvedRecipeIngredient(RecipeIngredient(id = 1, recipeId = 1, position = 0, productId = 101, grams = 200.0), chickenBreast),
            ResolvedRecipeIngredient(RecipeIngredient(id = 2, recipeId = 1, position = 1, productId = 102, grams = 100.0), whiteRice),
        )

        val result = RecipeCalculator.calculate(
            ingredients = ingredients,
            cookedWeightG = null,
            servings = null,
        )

        assertEquals(300.0, result.rawWeightG, 0.001)
        assertEquals(300.0, result.baseWeightG, 0.001)
        assertFalse(result.isCookedWeightUsed)

        assertEquals(590.0, result.totalKcal, 0.001)
        assertEquals(52.0, result.totalProtein, 0.001)
        assertEquals(78.0, result.totalCarbs, 0.001)
        assertEquals(6.0, result.totalFat, 0.001)

        // Per 100g = total * 100 / 300
        assertEquals(590.0 / 3.0, result.kcal100g, 0.001)
        assertEquals(52.0 / 3.0, result.protein100g, 0.001)
        assertEquals(78.0 / 3.0, result.carbs100g, 0.001)
        assertEquals(6.0 / 3.0, result.fat100g, 0.001)

        // Both chicken and rice have fiber, so fiber is present
        assertNotNull(result.totalFiber)
        assertNotNull(result.fiber100g)
        assertEquals((0.0 * 2 + 1.5 * 1) / 3.0, result.fiber100g!!, 0.001)

        assertNull(result.servings)
        assertNull(result.servingG)
    }

    @Test
    fun `calculation with cooked weight scales per 100g according to final weight`() {
        // Raw weight = 300g, but water evaporated so cooked weight is 250g
        val ingredients = listOf(
            ResolvedRecipeIngredient(RecipeIngredient(id = 1, recipeId = 1, position = 0, productId = 101, grams = 200.0), chickenBreast),
            ResolvedRecipeIngredient(RecipeIngredient(id = 2, recipeId = 1, position = 1, productId = 102, grams = 100.0), whiteRice),
        )

        val result = RecipeCalculator.calculate(
            ingredients = ingredients,
            cookedWeightG = 250.0,
            servings = null,
        )

        assertEquals(300.0, result.rawWeightG, 0.001)
        assertEquals(250.0, result.baseWeightG, 0.001)
        assertTrue(result.isCookedWeightUsed)

        // Total macros stay the same
        assertEquals(590.0, result.totalKcal, 0.001)

        // Per 100g scales by 100 / 250 = 0.4
        assertEquals(590.0 * 100.0 / 250.0, result.kcal100g, 0.001)
        assertEquals(52.0 * 100.0 / 250.0, result.protein100g, 0.001)
        assertEquals(78.0 * 100.0 / 250.0, result.carbs100g, 0.001)
        assertEquals(6.0 * 100.0 / 250.0, result.fat100g, 0.001)
    }

    @Test
    fun `servings calculate servingG unit name and per-serving macros`() {
        val ingredients = listOf(
            ResolvedRecipeIngredient(RecipeIngredient(id = 1, recipeId = 1, position = 0, productId = 101, grams = 200.0), chickenBreast),
            ResolvedRecipeIngredient(RecipeIngredient(id = 2, recipeId = 1, position = 1, productId = 102, grams = 100.0), whiteRice),
        )

        val result = RecipeCalculator.calculate(
            ingredients = ingredients,
            cookedWeightG = 400.0, // Water absorbed
            servings = 2.0,
        )

        assertEquals(2.0, result.servings)
        assertEquals(200.0, result.servingG!!, 0.001) // 400g / 2 = 200g per serving
        assertEquals(295.0, result.servingKcal!!, 0.001) // 590 / 2
        assertEquals(26.0, result.servingProtein!!, 0.001) // 52 / 2
        assertEquals(39.0, result.servingCarbs!!, 0.001) // 78 / 2
        assertEquals(3.0, result.servingFat!!, 0.001) // 6 / 2
    }

    @Test
    fun `secondary nutrient is null if any ingredient lacks it`() {
        // Oil lacks fiber (fiber100g = null)
        val ingredients = listOf(
            ResolvedRecipeIngredient(RecipeIngredient(id = 1, recipeId = 1, position = 0, productId = 101, grams = 200.0), chickenBreast),
            ResolvedRecipeIngredient(RecipeIngredient(id = 2, recipeId = 1, position = 1, productId = 103, grams = 10.0), oliveOil),
        )

        val result = RecipeCalculator.calculate(
            ingredients = ingredients,
            cookedWeightG = null,
            servings = null,
        )

        assertNull(result.totalFiber)
        assertNull(result.fiber100g)

        // But sugars, satFat and salt are present in both chicken and oil!
        assertNotNull(result.totalSugars)
        assertNotNull(result.sugars100g)
        assertNotNull(result.totalSatFat)
        assertNotNull(result.satFat100g)
        assertNotNull(result.totalSalt)
        assertNotNull(result.salt100g)
    }

    @Test
    fun `empty ingredients list produces safe zeros`() {
        val result = RecipeCalculator.calculate(
            ingredients = emptyList(),
            cookedWeightG = null,
            servings = null,
        )

        assertEquals(0.0, result.rawWeightG, 0.001)
        assertEquals(0.0, result.baseWeightG, 0.001)
        assertEquals(0.0, result.kcal100g, 0.001)
        assertNull(result.fiber100g)
        assertNull(result.servingG)
    }

    @Test
    fun `validationResult produces advisory warnings through LabelValidator`() {
        // Imbalanced food: 100g of pure oil (900 kcal) with cookedWeight = 50g -> 1800 kcal/100g (exceeds 900)
        val ingredients = listOf(
            ResolvedRecipeIngredient(RecipeIngredient(id = 1, recipeId = 1, position = 0, productId = 103, grams = 100.0), oliveOil),
        )

        val result = RecipeCalculator.calculate(
            ingredients = ingredients,
            cookedWeightG = 50.0, // halves weight, doubles concentration to 1800 kcal/100g
            servings = null,
        )

        assertFalse(result.validationResult.isValid)
        assertTrue(result.validationResult.issues.any { it.rule == ValidationRule.KCAL_LEQ_900 })
    }

    @Test
    fun `buildRecipeProduct creates Product with source RECIPE and serving units`() {
        val recipe = Recipe(
            id = 42L,
            productId = 1001L,
            name = "Pollo al curry",
            cookedWeightG = 500.0,
            servings = 2.0,
        )

        val ingredients = listOf(
            ResolvedRecipeIngredient(RecipeIngredient(id = 1, recipeId = 42, position = 0, productId = 101, grams = 400.0), chickenBreast),
        )

        val calculation = RecipeCalculator.calculate(ingredients, recipe.cookedWeightG, recipe.servings)
        val product = RecipeCalculator.buildRecipeProduct(recipe, calculation, currentTimeMillis = 5000L)

        assertEquals(1001L, product.id)
        assertEquals("Pollo al curry", product.name)
        assertEquals(ProductSource.RECIPE, product.source)
        assertEquals("ración", product.unitName)
        assertEquals(250.0, product.unitG!!, 0.001)
        assertEquals(250.0, product.servingG!!, 0.001)
        assertEquals(5000L, product.createdAt)
        assertEquals(5000L, product.updatedAt)
    }
}
