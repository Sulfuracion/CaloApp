package com.controlcal.core.model

/**
 * Domain model for macronutrient and calorie goals.
 */
data class GoalProfile(
    val id: Long = 0,
    val dayType: DayType,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val validFrom: String,
    val origin: GoalOrigin = GoalOrigin.MANUAL,
)

fun GoalProfile.toMacroTargets(): MacroTargets =
    MacroTargets(
        kcal = kcal,
        protein = proteinG,
        carbs = carbsG,
        fat = fatG,
    )
