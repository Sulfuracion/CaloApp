package com.controlcal.core.suggest

import com.controlcal.core.model.ResolvedSavedMeal
import com.controlcal.core.model.SavedMeal

/**
 * Pure ranking engine for saved meals.
 *
 * Orders meals by recent usage and frequency:
 * 1. Meals with a recorded [SavedMeal.lastUsedAt] appear before never-used meals,
 *    sorted descending by [SavedMeal.lastUsedAt].
 * 2. Ties in recent usage are broken by [SavedMeal.useCount] descending.
 * 3. Meals that have never been used are sorted by [SavedMeal.createdAt] descending.
 */
object SavedMealRanker {

    private val savedMealComparator = Comparator<SavedMeal> { a, b ->
        val aLast = a.lastUsedAt
        val bLast = b.lastUsedAt
        when {
            aLast != null && bLast != null -> {
                val recencyDiff = bLast.compareTo(aLast)
                if (recencyDiff != 0) recencyDiff else b.useCount.compareTo(a.useCount)
            }
            aLast != null && bLast == null -> -1
            aLast == null && bLast != null -> 1
            else -> b.createdAt.compareTo(a.createdAt)
        }
    }

    private val resolvedMealComparator = Comparator<ResolvedSavedMeal> { a, b ->
        savedMealComparator.compare(a.meal, b.meal)
    }

    fun rank(meals: List<SavedMeal>): List<SavedMeal> =
        meals.sortedWith(savedMealComparator)

    fun rankResolved(meals: List<ResolvedSavedMeal>): List<ResolvedSavedMeal> =
        meals.sortedWith(resolvedMealComparator)
}
