package com.controlcal.core.suggest

/**
 * Domain representation of a quick-log suggestion ranked for the current time slot.
 * [targetMeal] is the string identifier of the suggested meal.
 */
data class QuickLogSuggestion(
    val productId: Long,
    val productName: String,
    val defaultGrams: Double,
    val score: Double,
    val targetMeal: String,
    val lastLoggedAt: Long,
    val isLiquid: Boolean = false,
)
