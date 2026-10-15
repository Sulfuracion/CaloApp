package com.controlcal.core.pantry

/**
 * Validation errors for pantry portion limits and custom units.
 */
enum class PantryError {
    NEGATIVE_VALUE,
    MIN_EXCEEDS_MAX,
    UNIT_NAME_REQUIRED,
    UNIT_GRAMS_REQUIRED_OR_INVALID,
}

/**
 * Result of validating pantry portion limits and custom units.
 */
sealed interface PantryValidationResult {
    data class Valid(
        val minG: Double?,
        val maxG: Double?,
        val unitName: String?,
        val unitG: Double?,
    ) : PantryValidationResult

    data class Invalid(
        val minGError: PantryError? = null,
        val maxGError: PantryError? = null,
        val unitNameError: PantryError? = null,
        val unitGError: PantryError? = null,
    ) : PantryValidationResult
}

/**
 * Pure validator for pantry serving limits and custom unit definitions.
 * Follows PLAN.md section 5.1 and 5.6.
 */
object PantryLimitsValidator {

    fun validate(
        minG: Double?,
        maxG: Double?,
        unitName: String?,
        unitG: Double?,
    ): PantryValidationResult {
        var minGError: PantryError? = null
        var maxGError: PantryError? = null
        var unitNameError: PantryError? = null
        var unitGError: PantryError? = null

        val cleanUnitName = unitName?.trim()?.ifBlank { null }

        if (minG != null && minG < 0.0) {
            minGError = PantryError.NEGATIVE_VALUE
        }

        if (maxG != null && maxG < 0.0) {
            maxGError = PantryError.NEGATIVE_VALUE
        }

        if (minG != null && maxG != null && minG >= 0.0 && maxG >= 0.0 && minG > maxG) {
            minGError = PantryError.MIN_EXCEEDS_MAX
            maxGError = PantryError.MIN_EXCEEDS_MAX
        }

        if (cleanUnitName != null && (unitG == null || unitG <= 0.0)) {
            unitGError = PantryError.UNIT_GRAMS_REQUIRED_OR_INVALID
        }

        if (unitG != null) {
            if (unitG <= 0.0) {
                unitGError = PantryError.UNIT_GRAMS_REQUIRED_OR_INVALID
            }
            if (cleanUnitName == null) {
                unitNameError = PantryError.UNIT_NAME_REQUIRED
            }
        }

        return if (minGError != null || maxGError != null || unitNameError != null || unitGError != null) {
            PantryValidationResult.Invalid(
                minGError = minGError,
                maxGError = maxGError,
                unitNameError = unitNameError,
                unitGError = unitGError,
            )
        } else {
            PantryValidationResult.Valid(
                minG = minG,
                maxG = maxG,
                unitName = cleanUnitName,
                unitG = if (cleanUnitName != null) unitG else null,
            )
        }
    }
}
