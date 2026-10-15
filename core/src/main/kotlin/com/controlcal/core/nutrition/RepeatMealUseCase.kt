package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure domain use case for repeating a meal's entries on a target date.
 * Task M.5: Preserves the original time of day with the target date.
 */
class RepeatMealUseCase {

    operator fun invoke(
        sourceEntries: List<MealEntry>,
        targetDate: String,
        targetMeal: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<MealEntry> {
        val targetLocalDate = LocalDate.parse(targetDate)
        return sourceEntries.map { source ->
            val newLoggedAt = MealTimeCalculator.shiftToDate(source.loggedAt, targetLocalDate, zoneId)
            source.copy(
                id = 0L,
                date = targetDate,
                meal = targetMeal,
                loggedAt = newLoggedAt,
            )
        }
    }
}
