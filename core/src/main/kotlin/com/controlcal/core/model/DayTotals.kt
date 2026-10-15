package com.controlcal.core.model

/**
 * Aggregated totals for an entire day, grouped overall and by meal ID in [byMeal].
 */
data class DayTotals(
    val date: String,
    val totals: NutrientTotals,
    val byMeal: Map<String, MealTotals>,
)
