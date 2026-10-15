package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product

/**
 * Pure domain use case for Tare Mode (Modo tara / Restar sobras) from section 5.1 of docs/PLAN.md.
 * Creates a negative [MealEntry] for the given [product] and [leftoverGrams].
 */
class CreateTareEntryUseCase {

    /**
     * Creates a negative [MealEntry] representing leftovers.
     *
     * @param product The product being subtracted.
     * @param leftoverGrams Strictly positive number of leftover grams to subtract.
     * @param date ISO date string (YYYY-MM-DD).
     * @param meal Target meal ID.
     * @param loggedAt Millis timestamp when logged.
     * @throws IllegalArgumentException if [leftoverGrams] <= 0.0.
     */
    operator fun invoke(
        product: Product,
        leftoverGrams: Double,
        date: String,
        meal: String,
        loggedAt: Long = System.currentTimeMillis(),
    ): MealEntry {
        require(leftoverGrams > 0.0) { "Leftover grams must be strictly positive" }

        val portion = NutritionCalculator.calculatePortionMacros(product, -leftoverGrams)

        return MealEntry(
            id = 0L,
            date = date,
            meal = meal,
            productId = product.id,
            productName = product.name,
            grams = -leftoverGrams,
            kcal = portion.kcal,
            protein = portion.protein,
            carbs = portion.carbs,
            fat = portion.fat,
            loggedAt = loggedAt,
        )
    }
}
