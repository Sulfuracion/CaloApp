package com.controlcal.core.meal

import com.controlcal.core.model.ConfiguredMeal

/**
 * Pure domain use case for deactivating a meal.
 * Rules:
 * - Target meal must exist
 * - At least one active meal must always remain
 */
class DeactivateMealUseCase {

    operator fun invoke(
        currentMeals: List<ConfiguredMeal>,
        mealId: String,
    ): MealConfigResult {
        val target = currentMeals.find { it.id == mealId }
            ?: return MealConfigResult.Failure.MealNotFound()

        if (!target.isActive) {
            return MealConfigResult.Success(currentMeals)
        }

        val activeCount = currentMeals.count { it.isActive }
        if (activeCount <= 1) {
            return MealConfigResult.Failure.AtLeastOneActiveRequired()
        }

        val updated = currentMeals.map { meal ->
            if (meal.id == mealId) meal.copy(isActive = false) else meal
        }

        return MealConfigResult.Success(updated)
    }
}
