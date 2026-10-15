package com.controlcal.core.model

import java.time.LocalTime

/**
 * Domain model for a user-configured meal (section 4 and 5.1 of docs/PLAN.md).
 *
 * @property id Stable identifier (e.g. "BREAKFAST" or UUID string).
 * @property name User-facing meal name (e.g. "Desayuno", "Almuerzo").
 * @property startTime Start time used for sorting and time-of-day preselection.
 * @property isActive Whether this meal is active for logging.
 */
data class ConfiguredMeal(
    val id: String,
    val name: String,
    val startTime: LocalTime,
    val isActive: Boolean = true,
)
