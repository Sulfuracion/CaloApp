package com.controlcal.core.model

/**
 * Aggregated totals and entries for a specific meal [meal] (identified by its string ID).
 */
data class MealTotals(
    val meal: String,
    val totals: NutrientTotals,
    val entries: List<MealEntry> = emptyList(),
)
