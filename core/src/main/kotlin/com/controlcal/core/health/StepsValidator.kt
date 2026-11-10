package com.controlcal.core.health

sealed interface StepsValidationResult {
    data class Valid(val steps: Int) : StepsValidationResult
    sealed interface Invalid : StepsValidationResult {
        data object Blank : Invalid
        data object InvalidFormat : Invalid
        data object Negative : Invalid
        data object OutOfRange : Invalid
    }
}

object StepsValidator {
    const val MIN_STEPS = 0
    const val MAX_STEPS = 200_000

    fun validate(input: String?): StepsValidationResult {
        if (input.isNullOrBlank()) return StepsValidationResult.Invalid.Blank
        val sanitized = input.trim().replace(".", "").replace(" ", "")
        val parsed = sanitized.toIntOrNull() ?: return StepsValidationResult.Invalid.InvalidFormat
        if (parsed < MIN_STEPS) return StepsValidationResult.Invalid.Negative
        if (parsed > MAX_STEPS) return StepsValidationResult.Invalid.OutOfRange
        return StepsValidationResult.Valid(parsed)
    }

    fun isValid(input: String?): Boolean = validate(input) is StepsValidationResult.Valid
}
