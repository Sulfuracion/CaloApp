package com.controlcal.core.meal

import com.controlcal.core.model.ConfiguredMeal

/**
 * Pure domain use case for deleting a meal configuration.
 * Rules (docs/PLAN.md section 4 and 5.1):
 * - Target meal must exist
 * - A meal with existing entries cannot be deleted, only deactivated
 * - If the meal was active, at least one other active meal must remain
 */
class DeleteMealConfigUseCase {

    operator fun invoke(
        currentMeals: List<ConfiguredMeal>,
        mealId: String,
        hasEntries: Boolean,
    ): MealConfigResult {
        val target = currentMeals.find { it.id == mealId }
            ?: return MealConfigResult.Failure.MealNotFound()

        if (hasEntries) {
            return MealConfigResult.Failure.MealHasEntriesCannotDelete()
        }

        if (target.isActive && currentMeals.count { it.isActive } <= 1) {
            return MealConfigResult.Failure.AtLeastOneActiveRequired()
        }

        val updated = currentMeals.filterNot { it.id == mealId }
        return MealConfigResult.Success(updated)
    }
}
