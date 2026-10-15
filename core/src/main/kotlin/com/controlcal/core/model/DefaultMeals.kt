package com.controlcal.core.model

import java.time.LocalTime

/**
 * Provides the default meal configuration matching the preselection intervals
 * from task 1.6 and docs/PLAN.md section 4.
 */
object DefaultMeals {
    val BREAKFAST_TIME: LocalTime = LocalTime.of(6, 0)
    val LUNCH_TIME: LocalTime = LocalTime.of(12, 0)
    val SNACK_TIME: LocalTime = LocalTime.of(17, 0)
    val DINNER_TIME: LocalTime = LocalTime.of(21, 0)

    fun defaults(
        breakfastName: String = "Desayuno",
        lunchName: String = "Almuerzo",
        snackName: String = "Merienda",
        dinnerName: String = "Cena",
    ): List<ConfiguredMeal> = listOf(
        ConfiguredMeal(
            id = DefaultMealIds.BREAKFAST,
            name = breakfastName,
            startTime = BREAKFAST_TIME,
            isActive = true,
        ),
        ConfiguredMeal(
            id = DefaultMealIds.LUNCH,
            name = lunchName,
            startTime = LUNCH_TIME,
            isActive = true,
        ),
        ConfiguredMeal(
            id = DefaultMealIds.SNACK,
            name = snackName,
            startTime = SNACK_TIME,
            isActive = true,
        ),
        ConfiguredMeal(
            id = DefaultMealIds.DINNER,
            name = dinnerName,
            startTime = DINNER_TIME,
            isActive = true,
        ),
    )
}
