package com.controlcal.core.suggest

import com.controlcal.core.model.MealEntry
import com.controlcal.core.nutrition.MealClassifier
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min

/**
 * Pure domain logic for ranking products for quick-logging according to
 * docs/PLAN.md section 5.4:
 *
 * Scoring for each product for the current time slot:
 * - Number of times logged in the last 60 days
 * - Within ±90 minutes of the current time of day (circular time comparison)
 * - Double weight (x2.0) if the day of the week matches
 * - Linear decay by age over the 60-day window
 * - Mode (most frequent positive grams) calculated per product
 * - Top 6 products returned
 */
object QuickLogRanker {

    const val WINDOW_MINUTES: Int = 90
    const val MAX_AGE_DAYS: Int = 60
    private const val MILLIS_PER_DAY: Double = 24.0 * 60.0 * 60.0 * 1000.0
    private const val SIXTY_DAYS_MILLIS: Long = 60L * 24L * 60L * 60L * 1000L
    private const val MINUTES_PER_DAY: Int = 1440

    fun rank(
        entries: List<MealEntry>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        activeProductIds: Set<Long>? = null,
        liquidProductIds: Set<Long> = emptySet(),
        limit: Int = 6,
    ): List<QuickLogSuggestion> {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val nowMinutes = now.hour * 60 + now.minute
        val nowDayOfWeek = now.dayOfWeek
        val targetMeal = MealClassifier.suggestMealForHour(now.hour)

        data class ScoredEntry(
            val entry: MealEntry,
            val productId: Long,
            val score: Double,
        )

        val qualifyingEntries = entries.mapNotNull { entry ->
            val productId = entry.productId ?: return@mapNotNull null
            if (activeProductIds != null && productId !in activeProductIds) {
                return@mapNotNull null
            }

            val diffMillis = nowMillis - entry.loggedAt
            if (diffMillis !in 0L..SIXTY_DAYS_MILLIS) {
                return@mapNotNull null
            }

            val entryZonedDateTime = Instant.ofEpochMilli(entry.loggedAt).atZone(zoneId)
            val entryMinutes = entryZonedDateTime.hour * 60 + entryZonedDateTime.minute
            val diffMinutes = abs(nowMinutes - entryMinutes)
            val circularDiff = min(diffMinutes, MINUTES_PER_DAY - diffMinutes)
            if (circularDiff > WINDOW_MINUTES) {
                return@mapNotNull null
            }

            val dayMultiplier = if (entryZonedDateTime.dayOfWeek == nowDayOfWeek) 2.0 else 1.0
            val ageDays = diffMillis.toDouble() / MILLIS_PER_DAY
            val decay = maxOf(0.0, 1.0 - (ageDays / MAX_AGE_DAYS.toDouble()))
            val score = dayMultiplier * decay

            ScoredEntry(entry = entry, productId = productId, score = score)
        }

        val grouped = qualifyingEntries.groupBy { it.productId }

        val suggestions = grouped.map { (productId, productScoredEntries) ->
            val totalScore = productScoredEntries.sumOf { it.score }
            val latestLoggedAt = productScoredEntries.maxOf { it.entry.loggedAt }
            val latestEntry = productScoredEntries.maxByOrNull { it.entry.loggedAt }!!.entry

            val positiveGramsEntries = productScoredEntries
                .map { it.entry }
                .filter { it.grams > 0.0 }

            val mostFrequentGrams = if (positiveGramsEntries.isNotEmpty()) {
                positiveGramsEntries
                    .groupBy { it.grams }
                    .maxWithOrNull(
                        compareBy<Map.Entry<Double, List<MealEntry>>> { it.value.size }
                            .thenBy { it.value.maxOf { e -> e.loggedAt } }
                    )?.key ?: 100.0
            } else {
                100.0
            }

            QuickLogSuggestion(
                productId = productId,
                productName = latestEntry.productName,
                defaultGrams = mostFrequentGrams,
                score = totalScore,
                targetMeal = targetMeal,
                lastLoggedAt = latestLoggedAt,
                isLiquid = productId in liquidProductIds,
            )
        }

        return suggestions
            .sortedWith(
                compareByDescending<QuickLogSuggestion> { it.score }
                    .thenByDescending { it.lastLoggedAt }
            )
            .take(limit)
    }

    /**
     * Computes the most frequent products and their mode (most frequent positive grams)
     * for a given [mealId] over the provided history entries, falling back to all meals
     * if no positive-gram entries exist for [mealId].
     */
    fun rankHabitualsForMeal(
        entries: List<MealEntry>,
        mealId: String,
        activeProductIds: Set<Long>? = null,
        fallbackServingByProduct: Map<Long, Double> = emptyMap(),
        liquidProductIds: Set<Long> = emptySet(),
        limit: Int = 6,
    ): List<QuickLogSuggestion> {
        val validEntries = entries.filter { entry ->
            val pid = entry.productId ?: return@filter false
            entry.grams > 0.0 && (activeProductIds == null || pid in activeProductIds)
        }
        val mealEntries = validEntries.filter { it.meal == mealId }
        val sourceEntries = mealEntries.ifEmpty { validEntries }

        return sourceEntries
            .groupBy { it.productId!! }
            .map { (productId, prodEntries) ->
                val mostFrequentGrams = prodEntries
                    .groupBy { it.grams }
                    .maxByOrNull { it.value.size }
                    ?.key ?: fallbackServingByProduct[productId] ?: 100.0
                val latestEntry = prodEntries.maxByOrNull { it.loggedAt }!!
                QuickLogSuggestion(
                    productId = productId,
                    productName = latestEntry.productName,
                    defaultGrams = mostFrequentGrams,
                    score = prodEntries.size.toDouble(),
                    targetMeal = mealId,
                    lastLoggedAt = latestEntry.loggedAt,
                    isLiquid = productId in liquidProductIds,
                )
            }
            .take(limit)
    }
}
