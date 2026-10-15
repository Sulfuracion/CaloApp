package com.controlcal.core.nutrition

import com.controlcal.core.model.DayType
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.UserSettingsRepository
import java.time.LocalDate

/**
 * Resolves the effective [DayType] for a given [date].
 *
 * If a daily log entry exists for [date], its explicit [DayType] is used.
 * Otherwise, the default [DayType] for the day of week is resolved from the
 * user's weekly pattern in settings (section 4.3 of docs/PLAN.md).
 */
class ResolveDayTypeUseCase(
    private val dailyLogRepository: DailyLogRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    suspend operator fun invoke(date: LocalDate): DayType {
        val dateStr = date.toString()
        val dailyLog = dailyLogRepository.getByDate(dateStr)
        if (dailyLog != null) {
            return dailyLog.dayType
        }
        val pattern = userSettingsRepository.getWeeklyPattern()
        return pattern.dayTypeFor(date.dayOfWeek)
    }
}
