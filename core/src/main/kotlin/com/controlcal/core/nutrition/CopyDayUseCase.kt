package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure domain use case for copying all meal entries from a source day to a target day.
 * Task M.5: Preserves the original time of day with the target date.
 */
class CopyDayUseCase {

    operator fun invoke(
        sourceDayEntries: List<MealEntry>,
        targetDate: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<MealEntry> {
        val targetLocalDate = LocalDate.parse(targetDate)
        return sourceDayEntries.map { source ->
            val newLoggedAt = MealTimeCalculator.shiftToDate(source.loggedAt, targetLocalDate, zoneId)
            source.copy(
                id = 0L,
                date = targetDate,
                loggedAt = newLoggedAt,
            )
        }
    }
}
