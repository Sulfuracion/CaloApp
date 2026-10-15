package com.controlcal.core.model

/**
 * Stable identifiers for the default meals, matching the values previously stored
 * by Room when using [Meal] enum (section 4 of docs/PLAN.md).
 * Existing database entries use these exact strings.
 */
object DefaultMealIds {
    const val BREAKFAST = "BREAKFAST"
    const val LUNCH = "LUNCH"
    const val SNACK = "SNACK"
    const val DINNER = "DINNER"

    val ALL: List<String> = listOf(BREAKFAST, LUNCH, SNACK, DINNER)
}
