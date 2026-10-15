package com.controlcal.core.meal

import com.controlcal.core.model.ConfiguredMeal

/**
 * Pure domain use case for reactivating an inactive meal.
 * Rules:
 * - Target meal must exist
 * - Its start time must not clash with any currently active meal
 */
class ReactivateMealUseCase {

    operator fun invoke(
        currentMeals: List<ConfiguredMeal>,
        mealId: String,
    ): MealConfigResult {
        val target = currentMeals.find { it.id == mealId }
            ?: return MealConfigResult.Failure.MealNotFound()

        if (target.isActive) {
            return MealConfigResult.Success(currentMeals)
        }

        if (currentMeals.any { it.id != mealId && it.isActive && it.startTime == target.startTime }) {
            return MealConfigResult.Failure.ConflictingStartTime()
        }

        val updated = currentMeals.map { meal ->
            if (meal.id == mealId) meal.copy(isActive = true) else meal
        }

        return MealConfigResult.Success(updated)
    }
}
