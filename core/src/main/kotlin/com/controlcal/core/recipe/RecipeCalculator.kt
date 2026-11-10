package com.controlcal.core.recipe

import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.model.Recipe
import com.controlcal.core.model.ResolvedRecipeIngredient
import com.controlcal.core.ocr.LabelValidationResult
import com.controlcal.core.ocr.LabelValidator
import com.controlcal.core.ocr.model.OcrParsedNutrients

/**
 * Result of calculating nutritional values and portion information for a recipe.
 */
data class RecipeCalculationResult(
    val rawWeightG: Double,
    val baseWeightG: Double,
    val isCookedWeightUsed: Boolean,
    val totalKcal: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val totalFiber: Double?,
    val totalSugars: Double?,
    val totalSatFat: Double?,
    val totalSalt: Double?,
    val kcal100g: Double,
    val protein100g: Double,
    val carbs100g: Double,
    val fat100g: Double,
    val fiber100g: Double?,
    val sugars100g: Double?,
    val satFat100g: Double?,
    val salt100g: Double?,
    val servings: Double?,
    val servingG: Double?,
    val servingKcal: Double?,
    val servingProtein: Double?,
    val servingCarbs: Double?,
    val servingFat: Double?,
    val validationResult: LabelValidationResult,
)

/**
 * Pure calculation engine for recipes.
 *
 * Rules:
 * - Totals = sum of (ingredient.nutrient100g * grams / 100) for kcal, protein, carbs, fat.
 * - Secondary nutrients (fiber, sugars, satFat, salt) are included only if ALL ingredients provide them;
 *   if any ingredient has a null value, that total remains null.
 * - Base weight = cookedWeightG if provided and > 0; otherwise the sum of raw ingredient grams.
 * - Per 100g = total * 100 / baseWeight.
 * - Servings: if servings > 0, serving_g = baseWeight / servings, and unit_name = "ración".
 * - Validates the per-100g result through [LabelValidator] as an advisory check (non-blocking).
 */
object RecipeCalculator {

    fun calculate(
        ingredients: List<ResolvedRecipeIngredient>,
        cookedWeightG: Double?,
        servings: Double?,
    ): RecipeCalculationResult {
        val validIngredients = ingredients.filter { it.grams > 0.0 }
        val rawWeight = validIngredients.sumOf { it.grams }

        val hasCookedWeight = cookedWeightG != null && cookedWeightG > 0.0
        val baseWeight = if (hasCookedWeight) cookedWeightG else rawWeight

        var sumKcal = 0.0
        var sumProtein = 0.0
        var sumCarbs = 0.0
        var sumFat = 0.0

        var sumFiber = 0.0
        var allHaveFiber = validIngredients.isNotEmpty()

        var sumSugars = 0.0
        var allHaveSugars = validIngredients.isNotEmpty()

        var sumSatFat = 0.0
        var allHaveSatFat = validIngredients.isNotEmpty()

        var sumSalt = 0.0
        var allHaveSalt = validIngredients.isNotEmpty()

        for (item in validIngredients) {
            val factor = item.grams / 100.0
            sumKcal += item.product.kcal100g * factor
            sumProtein += item.product.protein100g * factor
            sumCarbs += item.product.carbs100g * factor
            sumFat += item.product.fat100g * factor

            val fiber = item.product.fiber100g
            if (fiber != null) sumFiber += fiber * factor else allHaveFiber = false

            val sugars = item.product.sugars100g
            if (sugars != null) sumSugars += sugars * factor else allHaveSugars = false

            val satFat = item.product.satFat100g
            if (satFat != null) sumSatFat += satFat * factor else allHaveSatFat = false

            val salt = item.product.salt100g
            if (salt != null) sumSalt += salt * factor else allHaveSalt = false
        }

        val totalFiber = if (allHaveFiber && validIngredients.isNotEmpty()) sumFiber else null
        val totalSugars = if (allHaveSugars && validIngredients.isNotEmpty()) sumSugars else null
        val totalSatFat = if (allHaveSatFat && validIngredients.isNotEmpty()) sumSatFat else null
        val totalSalt = if (allHaveSalt && validIngredients.isNotEmpty()) sumSalt else null

        val (k100, p100, c100, f100, fib100, sug100, sat100, salt100) = if (baseWeight > 0.0) {
            val scale = 100.0 / baseWeight
            Tuple8(
                sumKcal * scale,
                sumProtein * scale,
                sumCarbs * scale,
                sumFat * scale,
                totalFiber?.times(scale),
                totalSugars?.times(scale),
                totalSatFat?.times(scale),
                totalSalt?.times(scale),
            )
        } else {
            Tuple8(0.0, 0.0, 0.0, 0.0, null, null, null, null)
        }

        val validServings = if (servings != null && servings > 0.0) servings else null
        val servingG = if (validServings != null && baseWeight > 0.0) baseWeight / validServings else null

        val servingKcal = if (validServings != null) sumKcal / validServings else null
        val servingProtein = if (validServings != null) sumProtein / validServings else null
        val servingCarbs = if (validServings != null) sumCarbs / validServings else null
        val servingFat = if (validServings != null) sumFat / validServings else null

        val ocrNutrients = OcrParsedNutrients(
            kcal = k100,
            protein = p100,
            carbs = c100,
            fat = f100,
            fiber = fib100,
            sugars = sug100,
            satFat = sat100,
            salt = salt100,
        )
        val validationResult = LabelValidator.validate(ocrNutrients)

        return RecipeCalculationResult(
            rawWeightG = rawWeight,
            baseWeightG = baseWeight,
            isCookedWeightUsed = hasCookedWeight,
            totalKcal = sumKcal,
            totalProtein = sumProtein,
            totalCarbs = sumCarbs,
            totalFat = sumFat,
            totalFiber = totalFiber,
            totalSugars = totalSugars,
            totalSatFat = totalSatFat,
            totalSalt = totalSalt,
            kcal100g = k100,
            protein100g = p100,
            carbs100g = c100,
            fat100g = f100,
            fiber100g = fib100,
            sugars100g = sug100,
            satFat100g = sat100,
            salt100g = salt100,
            servings = validServings,
            servingG = servingG,
            servingKcal = servingKcal,
            servingProtein = servingProtein,
            servingCarbs = servingCarbs,
            servingFat = servingFat,
            validationResult = validationResult,
        )
    }

    /**
     * Creates or updates the associated [Product] representation of a [Recipe].
     */
    fun buildRecipeProduct(
        recipe: Recipe,
        calculation: RecipeCalculationResult,
        existingProduct: Product? = null,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Product {
        val unitName = if (calculation.servingG != null) "ración" else null
        val unitG = calculation.servingG
        val servingG = calculation.servingG

        return Product(
            id = existingProduct?.id ?: recipe.productId,
            barcode = existingProduct?.barcode,
            name = recipe.name.trim(),
            brand = existingProduct?.brand,
            kcal100g = calculation.kcal100g,
            protein100g = calculation.protein100g,
            carbs100g = calculation.carbs100g,
            fat100g = calculation.fat100g,
            fiber100g = calculation.fiber100g,
            sugars100g = calculation.sugars100g,
            satFat100g = calculation.satFat100g,
            salt100g = calculation.salt100g,
            isLiquid = false,
            unitName = unitName,
            unitG = unitG,
            servingG = servingG,
            minG = existingProduct?.minG,
            maxG = existingProduct?.maxG,
            inPantry = existingProduct?.inPantry ?: false,
            source = ProductSource.RECIPE,
            archived = recipe.archived,
            createdAt = existingProduct?.createdAt?.takeIf { it > 0 } ?: currentTimeMillis,
            updatedAt = currentTimeMillis,
        )
    }

    private data class Tuple8(
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val fiber: Double?,
        val sugars: Double?,
        val satFat: Double?,
        val salt: Double?,
    )
}
