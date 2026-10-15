package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure domain logic for meal time and logged_at calculations according to Task M.5:
 * - Meal time is that of its earliest entry; if no entries, current time if today,
 *   or configured start time if another day.
 * - Unified function to calculate logged_at for any entry creation:
 *   date + meal time in the device's timezone, always falling strictly within date.
 * - Repetitions (repeat meal, copy day) preserve the original time with the new date.
 */
object MealTimeCalculator {

    /**
     * Resolves the meal time for a given [date] and meal according to Task M.5:
     * 1. If [existingEntries] is not empty, returns the LocalTime of the earliest entry.
     * 2. If [existingEntries] is empty:
     *    - If [date] is today (according to [clock]), returns current LocalTime.
     *    - If [date] is another day, returns [configuredStartTime] (or 12:00 if null).
     */
    fun resolveMealTime(
        date: LocalDate,
        existingEntries: List<MealEntry>,
        configuredStartTime: LocalTime?,
        zoneId: ZoneId = ZoneId.systemDefault(),
        clock: Clock = Clock.systemDefaultZone(),
    ): LocalTime {
        if (existingEntries.isNotEmpty()) {
            val earliest = existingEntries.minByOrNull { it.loggedAt }!!
            return Instant.ofEpochMilli(earliest.loggedAt).atZone(zoneId).toLocalTime()
        }
        val today = LocalDate.now(clock)
        return if (date == today) {
            LocalTime.now(clock)
        } else {
            configuredStartTime ?: LocalTime.of(12, 0)
        }
    }

    /**
     * Converts [date] and [mealTime] in [zoneId] to epoch milliseconds.
     * Always falls strictly within [date] in [zoneId].
     */
    fun calculateLoggedAt(
        date: LocalDate,
        mealTime: LocalTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return date.atTime(mealTime).atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Resolves the meal time from [existingEntries] and [configuredStartTime]
     * and calculates the resulting epoch milliseconds for [date].
     */
    fun calculateLoggedAt(
        date: LocalDate,
        existingEntries: List<MealEntry>,
        configuredStartTime: LocalTime?,
        zoneId: ZoneId = ZoneId.systemDefault(),
        clock: Clock = Clock.systemDefaultZone(),
    ): Long {
        val mealTime = resolveMealTime(
            date = date,
            existingEntries = existingEntries,
            configuredStartTime = configuredStartTime,
            zoneId = zoneId,
            clock = clock,
        )
        return calculateLoggedAt(date, mealTime, zoneId)
    }

    /**
     * Shifts [sourceLoggedAt] to [targetDate] while preserving the local time of day
     * in [zoneId], ensuring the new timestamp falls strictly within [targetDate].
     */
    fun shiftToDate(
        sourceLoggedAt: Long,
        targetDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val originalTime = Instant.ofEpochMilli(sourceLoggedAt).atZone(zoneId).toLocalTime()
        return calculateLoggedAt(targetDate, originalTime, zoneId)
    }
}
