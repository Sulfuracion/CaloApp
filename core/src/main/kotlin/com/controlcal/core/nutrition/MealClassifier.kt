package com.controlcal.core.nutrition

import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMealIds
import java.time.LocalTime

/**
 * Pure domain logic for classifying meals and suggesting the active meal
 * based on the time of day according to docs/PLAN.md section 5.1:
 * "El orden y la comida preseleccionada salen de la hora de inicio;
 * antes de la primera, se preselecciona la última del día."
 */
object MealClassifier {

    /**
     * Suggests a default meal id based on the hour of day (0..23).
     * Maintains backward compatibility with task 1.6:
     * - 06:00 to 11:59 -> BREAKFAST
     * - 12:00 to 16:59 -> LUNCH
     * - 17:00 to 20:59 -> SNACK
     * - 21:00 to 05:59 -> DINNER
     */
    fun suggestMealForHour(hour: Int): String {
        val normalized = ((hour % 24) + 24) % 24
        return when (normalized) {
            in 6..11 -> DefaultMealIds.BREAKFAST
            in 12..16 -> DefaultMealIds.LUNCH
            in 17..20 -> DefaultMealIds.SNACK
            else -> DefaultMealIds.DINNER
        }
    }

    /**
     * Suggests the active meal based on [time] and the user's [meals] configuration.
     *
     * Rule from docs/PLAN.md section 5.1:
     * Active meals are ordered by start time. Before the first meal of the day,
     * the last meal of the day is preselected. Otherwise, the latest meal whose
     * start time has already passed is selected.
     */
    fun suggestMeal(meals: List<ConfiguredMeal>, time: LocalTime): ConfiguredMeal? {
        val activeMeals = meals.filter { it.isActive }.sortedBy { it.startTime }
        if (activeMeals.isEmpty()) return null

        val first = activeMeals.first()
        if (time < first.startTime) {
            return activeMeals.last()
        }

        return activeMeals.lastOrNull { it.startTime <= time } ?: activeMeals.last()
    }
}
