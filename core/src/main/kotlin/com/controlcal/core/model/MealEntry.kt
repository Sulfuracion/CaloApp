package com.controlcal.core.model

/**
 * Domain model for a logged food entry on a given date and meal.
 * [meal] is the stable identifier of a configured meal (section 4 of docs/PLAN.md).
 * [grams] can be negative in tare mode.
 */
data class MealEntry(
    val id: Long = 0,
    val date: String,
    val meal: String,
    val productId: Long? = null,
    val productName: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val loggedAt: Long,
) {
    val isQuickCalories: Boolean get() = productId == null
    val isTare: Boolean get() = grams < 0.0
}
