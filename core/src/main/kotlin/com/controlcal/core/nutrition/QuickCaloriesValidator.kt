package com.controlcal.core.nutrition

import kotlin.math.abs

data class QuickCaloriesValidationResult(
    val isValid: Boolean,
    val kcalError: String? = null,
    val hasAtwaterWarning: Boolean = false,
    val atwaterDifferencePercentage: Double = 0.0,
    val calculatedAtwaterKcal: Double = 0.0,
)

object QuickCaloriesValidator {

    const val DEFAULT_DESCRIPTION = "Calorías rápidas"
    const val ATWATER_DEVIATION_THRESHOLD = 0.15 // 15%

    /**
     * Validates quick calories input according to Task M.6:
     * - [kcal] must be non-null and > 0.0.
     * - [protein], [carbs], [fat] default to 0.0 if not provided or empty.
     * - [hasProteinInput], [hasCarbsInput], [hasFatInput]: whether the user explicitly entered a value.
     * - If all three macros are provided and 4·P + 4·C + 9·G differs from kcal by > 15%, warns without blocking.
     * - If partial macros are provided, warns only if their kcal (4·P + 4·C + 9·G) exceeds total kcal by > 15%.
     */
    fun validate(
        kcal: Double?,
        protein: Double? = 0.0,
        carbs: Double? = 0.0,
        fat: Double? = 0.0,
        hasProteinInput: Boolean = false,
        hasCarbsInput: Boolean = false,
        hasFatInput: Boolean = false,
    ): QuickCaloriesValidationResult {
        if (kcal == null || kcal <= 0.0) {
            return QuickCaloriesValidationResult(
                isValid = false,
                kcalError = "Las calorías son obligatorias y deben ser mayores que 0",
            )
        }

        val p = protein ?: 0.0
        val c = carbs ?: 0.0
        val f = fat ?: 0.0
        val atwaterKcal = 4.0 * p + 4.0 * c + 9.0 * f

        val allThreeEntered = hasProteinInput && hasCarbsInput && hasFatInput
        val anyEntered = hasProteinInput || hasCarbsInput || hasFatInput

        val hasWarning: Boolean
        val diffPercentage: Double

        if (allThreeEntered) {
            val diff = abs(atwaterKcal - kcal)
            diffPercentage = diff / kcal
            hasWarning = diffPercentage > ATWATER_DEVIATION_THRESHOLD
        } else if (anyEntered) {
            // Partial macros: warn ONLY if macros kcal exceed total kcal by > 15%
            val excess = atwaterKcal - kcal
            diffPercentage = if (excess > 0.0) excess / kcal else 0.0
            hasWarning = diffPercentage > ATWATER_DEVIATION_THRESHOLD
        } else {
            hasWarning = false
            diffPercentage = 0.0
        }

        return QuickCaloriesValidationResult(
            isValid = true,
            hasAtwaterWarning = hasWarning,
            atwaterDifferencePercentage = diffPercentage,
            calculatedAtwaterKcal = atwaterKcal,
        )
    }

    fun sanitizeDescription(rawDescription: String?): String {
        return rawDescription?.trim()?.ifEmpty { null } ?: DEFAULT_DESCRIPTION
    }
}
