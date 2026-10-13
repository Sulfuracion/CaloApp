package com.controlcal.core.model

/**
 * Aggregated totals for an entire day, grouped overall and by [Meal].
 */
data class DayTotals(
    val date: String,
    val totals: NutrientTotals,
    val byMeal: Map<Meal, MealTotals>,
)
