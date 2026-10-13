package com.controlcal.core.nutrition

import kotlin.math.roundToLong

/**
 * Helper for decimal formatting and parsing with comma decimal separator,
 * fulfilling project rule 7: "Decimales con coma en UI".
 */
object NumberFormatHelper {

    /**
     * Formats a [Double] into a user-facing string using a comma as decimal separator.
     * Whole numbers are formatted without decimals (e.g. 100.0 -> "100").
     * Numbers with decimals format up to 2 decimal places (e.g. 12.5 -> "12,5", 0.75 -> "0,75").
     */
    fun formatDecimal(value: Double?): String {
        if (value == null) return ""
        val factor = 100.0
        val rounded = (value * factor).roundToLong() / factor
        val text = rounded.toString()
        val parts = text.split('.')
        return if (parts.size == 2 && parts[1] == "0") {
            parts[0]
        } else {
            text.replace('.', ',')
        }
    }

    /**
     * Parses a string that may use either comma or dot as decimal separator.
     * Returns null if the string is blank or does not represent a valid number.
     */
    fun parseDecimal(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val normalized = text.trim().replace(',', '.')
        return normalized.toDoubleOrNull()
    }
}
