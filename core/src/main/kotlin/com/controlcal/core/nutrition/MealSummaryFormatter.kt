package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry

/**
 * Pure domain formatter that generates a concise 1-2 line summary of food entries
 * for meal cards (Samsung Health style).
 */
object MealSummaryFormatter {

    /**
     * Formats a list of [MealEntry] into a comma-separated list of product names.
     * Distinct product names are used to keep the summary concise.
     * Returns an empty string if no positive entries are found.
     */
    fun formatSummary(entries: List<MealEntry>): String {
        if (entries.isEmpty()) return ""

        val positiveNames = entries
            .filter { it.grams > 0.0 || (it.isQuickCalories && it.kcal > 0.0) }
            .map { it.productName.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        return positiveNames.joinToString(separator = ", ")
    }
}
