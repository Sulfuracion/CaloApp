package com.controlcal.core.meal

import com.controlcal.core.model.ConfiguredMeal

/**
 * Pure domain use case for renaming an existing meal.
 * Rules:
 * - Target meal must exist
 * - New name cannot be blank
 * - New name cannot duplicate another meal's name (case-insensitive)
 */
class RenameMealConfigUseCase {

    operator fun invoke(
        currentMeals: List<ConfiguredMeal>,
        mealId: String,
        newName: String,
    ): MealConfigResult {
        if (currentMeals.none { it.id == mealId }) {
            return MealConfigResult.Failure.MealNotFound()
        }

        val cleanName = newName.trim()
        if (cleanName.isEmpty()) {
            return MealConfigResult.Failure.EmptyName()
        }

        if (currentMeals.any { it.id != mealId && it.name.trim().equals(cleanName, ignoreCase = true) }) {
            return MealConfigResult.Failure.DuplicateName()
        }

        val updated = currentMeals.map { meal ->
            if (meal.id == mealId) meal.copy(name = cleanName) else meal
        }

        return MealConfigResult.Success(updated)
    }
}
