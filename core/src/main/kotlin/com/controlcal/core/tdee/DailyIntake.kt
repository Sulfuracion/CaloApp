package com.controlcal.core.tdee

/**
 * Represents the total food intake for a specific [date] (yyyy-MM-dd).
 * [isComplete] indicates whether the user logged all meals for that day.
 */
data class DailyIntake(
    val date: String,
    val kcal: Double,
    val isComplete: Boolean = true,
)
