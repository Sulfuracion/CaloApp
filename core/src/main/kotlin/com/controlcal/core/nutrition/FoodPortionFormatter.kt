package com.controlcal.core.nutrition

import kotlin.math.abs

/**
 * Pure domain formatter for food portions and unit representations.
 * Adheres to rule 7 (decimal with comma) and supports discrete units (unit_g).
 */
object FoodPortionFormatter {

    /**
     * Formats a portion given [grams], and optional custom unit specification ([unitG], [unitName]).
     *
     * Examples:
     * - grams = 110.0, unitG = 55.0, unitName = "huevo" -> "2 huevo (110 g)"
     * - grams = -55.0, unitG = 55.0, unitName = "huevo" -> "-1 huevo (-55 g)"
     * - grams = 150.0, unitG = null -> "150 g"
     * - grams = -50.0, unitG = null -> "-50 g"
     */
    fun formatQuantity(
        grams: Double,
        unitG: Double? = null,
        unitName: String? = null,
        isLiquid: Boolean = false,
    ): String {
        val isNegative = grams < 0.0
        val baseUnit = if (isLiquid) "ml" else "g"

        if (unitG != null && unitG > 0.0) {
            val units = NutritionCalculator.gramsToUnits(abs(grams), unitG)
            val unitsFormatted = NumberFormatHelper.formatDecimal(units)
            val gramsFormatted = NumberFormatHelper.formatDecimal(abs(grams))
            val label = unitName?.trim()?.ifEmpty { null } ?: "ud"

            return if (isNegative) {
                "-$unitsFormatted $label (-$gramsFormatted $baseUnit)"
            } else {
                "$unitsFormatted $label ($gramsFormatted $baseUnit)"
            }
        }

        return "${NumberFormatHelper.formatDecimal(grams)} $baseUnit"
    }
}
