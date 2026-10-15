package com.controlcal.core.model

/**
 * Domain model for a logged body weight measurement on a specific calendar date.
 * [date] is in local ISO format (yyyy-MM-dd).
 * [weightKg] is the measured weight in kilograms.
 */
data class WeightEntry(
    val date: String,
    val weightKg: Double,
)
