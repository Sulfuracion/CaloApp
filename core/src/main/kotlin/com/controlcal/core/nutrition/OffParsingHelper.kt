package com.controlcal.core.nutrition

import java.util.Locale

/**
 * Pure helper functions for normalizing Open Food Facts product fields.
 */
object OffParsingHelper {

    const val KJ_TO_KCAL = 4.184

    private val servingSizeRegex = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(?:g|ml)\b""")
    private val literQuantityRegex = Regex("""\b\d+(?:[.,]\d+)?\s*l\b""")

    fun convertKjToKcal(kj: Double): Double = kj / KJ_TO_KCAL

    fun parseServingGrams(servingSize: String?): Double? {
        if (servingSize.isNullOrBlank()) return null
        val match = servingSizeRegex.find(servingSize) ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
    }

    fun parseIsLiquid(servingSize: String?, quantity: String?): Boolean {
        val combined = "${servingSize.orEmpty()} ${quantity.orEmpty()}".lowercase(Locale.ROOT)
        return combined.contains("ml") ||
            combined.contains("cl") ||
            combined.contains("litro") ||
            combined.contains("liter") ||
            literQuantityRegex.containsMatchIn(combined)
    }
}
