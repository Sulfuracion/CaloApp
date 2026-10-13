package com.controlcal.core.model

/**
 * Aggregated totals and entries for a specific [Meal].
 */
data class MealTotals(
    val meal: Meal,
    val totals: NutrientTotals,
    val entries: List<MealEntry> = emptyList(),
)
