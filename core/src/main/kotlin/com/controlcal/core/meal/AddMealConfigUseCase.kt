package com.controlcal.core.meal

import com.controlcal.core.model.ConfiguredMeal
import java.time.LocalTime
import java.util.UUID

/**
 * Pure domain use case for adding a new configured meal.
 * Rules (docs/PLAN.md section 5.1):
 * - Name must not be blank
 * - Name must not duplicate an existing meal (case-insensitive)
 * - Two active meals cannot share the same start time
 */
class AddMealConfigUseCase {

    operator fun invoke(
        currentMeals: List<ConfiguredMeal>,
        name: String,
        startTime: LocalTime,
        isActive: Boolean = true,
        id: String = UUID.randomUUID().toString(),
    ): MealConfigResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return MealConfigResult.Failure.EmptyName()
        }

        if (currentMeals.any { it.name.trim().equals(cleanName, ignoreCase = true) }) {
            return MealConfigResult.Failure.DuplicateName()
        }

        if (isActive && currentMeals.any { it.isActive && it.startTime == startTime }) {
            return MealConfigResult.Failure.ConflictingStartTime()
        }

        val newMeal = ConfiguredMeal(
            id = id,
            name = cleanName,
            startTime = startTime,
            isActive = isActive,
        )

        return MealConfigResult.Success(currentMeals + newMeal)
    }
}
