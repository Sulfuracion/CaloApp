package com.controlcal.core.ocr

import com.controlcal.core.ocr.model.OcrParsedNutrients
import kotlin.math.abs

enum class ValidationRule {
    CALORIC_BALANCE,
    SUGARS_LEQ_CARBS,
    SAT_FAT_LEQ_FAT,
    VALUE_LEQ_100,
    KCAL_LEQ_900,
    NON_NEGATIVE
}

data class ValidationIssue(
    val field: String?,
    val rule: ValidationRule,
    val message: String
)

data class LabelValidationResult(
    val isValid: Boolean,
    val issues: List<ValidationIssue> = emptyList(),
    val invalidFields: Set<String> = emptySet()
)

object LabelValidator {

    /**
     * Validates parsed nutritional values per 100g/ml according to Section 5.3 Step 7 of PLAN.md:
     * 1. Caloric balance: 4*P + 4*C + 9*F + 2*fiber must be within 15% of kcal.
     * 2. Sugars must not exceed carbohydrates.
     * 3. Saturated fat must not exceed total fat.
     * 4. No nutrient value per 100g (protein, carbs, fat, sugars, satFat, fiber, salt) exceeds 100.
     * 5. Kcal must not exceed 900.
     * 6. Values must be non-negative.
     */
    fun validate(nutrients: OcrParsedNutrients): LabelValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val invalidFields = mutableSetOf<String>()

        // 1. Non-negativity check
        val nutrientMap = mapOf(
            "kcal" to nutrients.kcal,
            "protein" to nutrients.protein,
            "carbs" to nutrients.carbs,
            "fat" to nutrients.fat,
            "sugars" to nutrients.sugars,
            "satFat" to nutrients.satFat,
            "fiber" to nutrients.fiber,
            "salt" to nutrients.salt
        )

        for ((field, value) in nutrientMap) {
            if (value != null && value < 0.0) {
                issues.add(
                    ValidationIssue(
                        field = field,
                        rule = ValidationRule.NON_NEGATIVE,
                        message = "$field cannot be negative: $value"
                    )
                )
                invalidFields.add(field)
            }
        }

        // 2. Kcal <= 900
        nutrients.kcal?.let { kcal ->
            if (kcal > 900.0) {
                issues.add(
                    ValidationIssue(
                        field = "kcal",
                        rule = ValidationRule.KCAL_LEQ_900,
                        message = "Kcal cannot exceed 900 per 100g: $kcal"
                    )
                )
                invalidFields.add("kcal")
            }
        }

        // 3. No value per 100g exceeds 100 (excluding kcal)
        val per100gNutrients = mapOf(
            "protein" to nutrients.protein,
            "carbs" to nutrients.carbs,
            "fat" to nutrients.fat,
            "sugars" to nutrients.sugars,
            "satFat" to nutrients.satFat,
            "fiber" to nutrients.fiber,
            "salt" to nutrients.salt
        )

        for ((field, value) in per100gNutrients) {
            if (value != null && value > 100.0) {
                issues.add(
                    ValidationIssue(
                        field = field,
                        rule = ValidationRule.VALUE_LEQ_100,
                        message = "$field per 100g cannot exceed 100g: $value"
                    )
                )
                invalidFields.add(field)
            }
        }

        // 4. Saturated fat <= Fat
        if (nutrients.satFat != null && nutrients.fat != null) {
            if (nutrients.satFat > nutrients.fat + 0.05) {
                issues.add(
                    ValidationIssue(
                        field = "satFat",
                        rule = ValidationRule.SAT_FAT_LEQ_FAT,
                        message = "Saturated fat (${nutrients.satFat}) cannot exceed total fat (${nutrients.fat})"
                    )
                )
                invalidFields.add("satFat")
            }
        }

        // 5. Sugars <= Carbs
        if (nutrients.sugars != null && nutrients.carbs != null) {
            if (nutrients.sugars > nutrients.carbs + 0.05) {
                issues.add(
                    ValidationIssue(
                        field = "sugars",
                        rule = ValidationRule.SUGARS_LEQ_CARBS,
                        message = "Sugars (${nutrients.sugars}) cannot exceed total carbohydrates (${nutrients.carbs})"
                    )
                )
                invalidFields.add("sugars")
            }
        }

        // 6. Caloric balance: 4*P + 4*C + 9*F + 2*fiber must be within 15% of kcal
        if (nutrients.kcal != null && nutrients.kcal > 0.0 &&
            nutrients.protein != null && nutrients.carbs != null && nutrients.fat != null
        ) {
            val fiber = nutrients.fiber ?: 0.0
            val calculatedKcal = 4.0 * nutrients.protein + 4.0 * nutrients.carbs + 9.0 * nutrients.fat + 2.0 * fiber
            val diff = abs(calculatedKcal - nutrients.kcal)
            val maxAllowedDiff = 0.15 * nutrients.kcal

            if (diff > maxAllowedDiff) {
                issues.add(
                    ValidationIssue(
                        field = null,
                        rule = ValidationRule.CALORIC_BALANCE,
                        message = "Caloric balance mismatch: calculated $calculatedKcal kcal vs reported ${nutrients.kcal} kcal (diff: $diff > 15%)"
                    )
                )
                invalidFields.add("kcal")
                invalidFields.add("protein")
                invalidFields.add("carbs")
                invalidFields.add("fat")
            }
        }

        return LabelValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            invalidFields = invalidFields
        )
    }
}
