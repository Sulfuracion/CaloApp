package com.controlcal.core.model

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
