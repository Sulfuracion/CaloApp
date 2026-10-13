package com.controlcal.core.model

/**
 * Meals of the day, as defined by section 4 of docs/PLAN.md.
 *
 * These enums are persisted by Room as TEXT using their [name], so renaming
 * a constant would change the stored data. Names are part of the schema.
 */
enum class Meal {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
}

/** Type of day used to pick the goal profile. */
enum class DayType {
    TRAINING,
    REST,
}

/** Where a product came from. */
enum class ProductSource {
    MANUAL,
    OFF,
    OCR,
}

/** Where a goal profile came from. */
enum class GoalOrigin {
    MANUAL,
    TDEE,
}
