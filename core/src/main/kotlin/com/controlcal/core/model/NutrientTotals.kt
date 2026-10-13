package com.controlcal.core.model

/**
 * Immutable aggregated macronutrient values.
 */
data class NutrientTotals(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)
