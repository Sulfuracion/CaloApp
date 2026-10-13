package com.controlcal.core.nutrition

import com.controlcal.core.model.Meal

/**
 * Pure domain logic for classifying meals and suggesting the active meal
 * based on the time of day.
 */
object MealClassifier {

    /**
     * Suggests a default [Meal] based on the hour of day (0..23).
     *
     * - 06:00 to 11:59 -> BREAKFAST
     * - 12:00 to 16:59 -> LUNCH
     * - 17:00 to 20:59 -> SNACK
     * - 21:00 to 05:59 -> DINNER
     */
    fun suggestMealForHour(hour: Int): Meal {
        val normalized = ((hour % 24) + 24) % 24
        return when (normalized) {
            in 6..11 -> Meal.BREAKFAST
            in 12..16 -> Meal.LUNCH
            in 17..20 -> Meal.SNACK
            else -> Meal.DINNER
        }
    }
}
