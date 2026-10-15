package com.controlcal.core.meal

import com.controlcal.core.model.ConfiguredMeal
import java.time.LocalTime

/**
 * Pure domain use case for updating the start time of a meal.
 * Rules:
 * - Target meal must exist
 * - If the meal is active, the new start time must not clash with any other active meal
 */
class ChangeMealStartTimeUseCase {

    operator fun invoke(
        currentMeals: List<ConfiguredMeal>,
        mealId: String,
        newStartTime: LocalTime,
    ): MealConfigResult {
        val target = currentMeals.find { it.id == mealId }
            ?: return MealConfigResult.Failure.MealNotFound()

        if (target.isActive && currentMeals.any { it.id != mealId && it.isActive && it.startTime == newStartTime }) {
            return MealConfigResult.Failure.ConflictingStartTime()
        }

        val updated = currentMeals.map { meal ->
            if (meal.id == mealId) meal.copy(startTime = newStartTime) else meal
        }

        return MealConfigResult.Success(updated)
    }
}
