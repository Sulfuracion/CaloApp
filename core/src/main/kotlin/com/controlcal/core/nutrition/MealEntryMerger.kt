package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry

/**
 * Result of saving a meal entry: either created as a new entry or merged into an existing one.
 */
sealed interface MealEntrySaveResult {
    data class Created(val entry: MealEntry) : MealEntrySaveResult
    data class Merged(
        val mergedEntry: MealEntry,
        val previousEntry: MealEntry,
    ) : MealEntrySaveResult
}

/**
 * Pure domain merger and decision rule for meal entries (Task M.9).
 *
 * Rules:
 * - An entry is merged if and only if date, meal, and productId match, AND both
 *   existing and incoming entries have grams > 0.
 * - Tare entries (grams < 0), quick calories (productId == null), different meals,
 *   or different dates are never merged.
 * - If multiple existing entries match (legacy duplicates in the database), the incoming
 *   entry is merged into the earliest one (by loggedAt, then id).
 * - Grams, kcal, protein, carbs, and fat are incremented with the new values.
 * - The existing entry's values are not recalculated from current product definitions.
 * - The earliest loggedAt and the existing id are conserved.
 */
object MealEntryMerger {

    fun canMerge(existing: MealEntry, incoming: MealEntry): Boolean {
        return existing.date == incoming.date &&
            existing.meal == incoming.meal &&
            existing.productId != null &&
            incoming.productId != null &&
            existing.productId == incoming.productId &&
            existing.grams > 0.0 &&
            incoming.grams > 0.0
    }

    fun findMergeCandidate(existingEntries: List<MealEntry>, incoming: MealEntry): MealEntry? {
        return existingEntries
            .filter { canMerge(it, incoming) }
            .minWithOrNull(compareBy({ it.loggedAt }, { it.id }))
    }

    fun merge(existing: MealEntry, incoming: MealEntry): MealEntry {
        require(canMerge(existing, incoming)) { "Entries cannot be merged" }
        return existing.copy(
            grams = existing.grams + incoming.grams,
            kcal = existing.kcal + incoming.kcal,
            protein = existing.protein + incoming.protein,
            carbs = existing.carbs + incoming.carbs,
            fat = existing.fat + incoming.fat,
            loggedAt = minOf(existing.loggedAt, incoming.loggedAt),
        )
    }
}
