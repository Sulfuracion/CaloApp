package com.controlcal.core.nutrition

object GoalProfileValidator {

    fun validateKcal(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val parsed = NumberFormatHelper.parseDecimal(input) ?: return false
        return parsed > 0.0
    }

    fun validateMacro(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val parsed = NumberFormatHelper.parseDecimal(input) ?: return false
        return parsed >= 0.0
    }

    fun validateMinKcal(input: String?): Boolean = validateKcal(input)

    fun validateAll(
        minKcal: String?,
        trainingKcal: String?,
        trainingProtein: String?,
        trainingCarbs: String?,
        trainingFat: String?,
        restKcal: String?,
        restProtein: String?,
        restCarbs: String?,
        restFat: String?,
    ): Boolean {
        return validateMinKcal(minKcal) &&
            validateKcal(trainingKcal) &&
            validateMacro(trainingProtein) &&
            validateMacro(trainingCarbs) &&
            validateMacro(trainingFat) &&
            validateKcal(restKcal) &&
            validateMacro(restProtein) &&
            validateMacro(restCarbs) &&
            validateMacro(restFat)
    }
}
