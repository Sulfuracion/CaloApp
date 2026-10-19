package com.controlcal.core.weight

import com.controlcal.core.nutrition.NumberFormatHelper

sealed interface WeightValidationResult {
    data class Valid(val weightKg: Double) : WeightValidationResult
    sealed interface Invalid : WeightValidationResult {
        data object Blank : Invalid
        data object InvalidFormat : Invalid
        data object OutOfRange : Invalid
    }
}

object WeightValidator {
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 400.0

    fun validate(input: String?): WeightValidationResult {
        if (input.isNullOrBlank()) return WeightValidationResult.Invalid.Blank
        val parsed = NumberFormatHelper.parseDecimal(input)
            ?: return WeightValidationResult.Invalid.InvalidFormat
        if (parsed < MIN_WEIGHT_KG || parsed > MAX_WEIGHT_KG) {
            return WeightValidationResult.Invalid.OutOfRange
        }
        return WeightValidationResult.Valid(parsed)
    }

    fun isValid(input: String?): Boolean = validate(input) is WeightValidationResult.Valid
}
