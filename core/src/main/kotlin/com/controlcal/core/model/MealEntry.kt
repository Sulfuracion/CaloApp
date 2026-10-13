package com.controlcal.core.model

/**
 * Domain model for a logged food entry on a given date and meal.
 * [grams] can be negative in tare mode.
 */
data class MealEntry(
    val id: Long = 0,
    val date: String,
    val meal: Meal,
    val productId: Long,
    val productName: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val loggedAt: Long,
)
