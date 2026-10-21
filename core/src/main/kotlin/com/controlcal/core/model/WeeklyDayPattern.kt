package com.controlcal.core.model

import java.time.DayOfWeek

/**
 * Weekly default pattern mapping each [DayOfWeek] to its default [DayType].
 *
 * Section 4.3 of docs/PLAN.md: Each day is either training or rest, with a default
 * weekly pattern in settings.
 */
data class WeeklyDayPattern(
    val monday: DayType = DayType.TRAINING,
    val tuesday: DayType = DayType.REST,
    val wednesday: DayType = DayType.TRAINING,
    val thursday: DayType = DayType.REST,
    val friday: DayType = DayType.TRAINING,
    val saturday: DayType = DayType.REST,
    val sunday: DayType = DayType.REST,
) {
    fun dayTypeFor(dayOfWeek: DayOfWeek): DayType = when (dayOfWeek) {
        DayOfWeek.MONDAY -> monday
        DayOfWeek.TUESDAY -> tuesday
        DayOfWeek.WEDNESDAY -> wednesday
        DayOfWeek.THURSDAY -> thursday
        DayOfWeek.FRIDAY -> friday
        DayOfWeek.SATURDAY -> saturday
        DayOfWeek.SUNDAY -> sunday
    }

    fun withDayType(dayOfWeek: DayOfWeek, dayType: DayType): WeeklyDayPattern = when (dayOfWeek) {
        DayOfWeek.MONDAY -> copy(monday = dayType)
        DayOfWeek.TUESDAY -> copy(tuesday = dayType)
        DayOfWeek.WEDNESDAY -> copy(wednesday = dayType)
        DayOfWeek.THURSDAY -> copy(thursday = dayType)
        DayOfWeek.FRIDAY -> copy(friday = dayType)
        DayOfWeek.SATURDAY -> copy(saturday = dayType)
        DayOfWeek.SUNDAY -> copy(sunday = dayType)
    }

    fun serialize(): String = listOf(
        monday.name,
        tuesday.name,
        wednesday.name,
        thursday.name,
        friday.name,
        saturday.name,
        sunday.name,
    ).joinToString(",")

    companion object {
        fun parse(raw: String?): WeeklyDayPattern {
            if (raw.isNullOrBlank()) return WeeklyDayPattern()
            val parts = raw.split(",")
            if (parts.size != 7) return WeeklyDayPattern()
            return try {
                WeeklyDayPattern(
                    monday = DayType.valueOf(parts[0].trim()),
                    tuesday = DayType.valueOf(parts[1].trim()),
                    wednesday = DayType.valueOf(parts[2].trim()),
                    thursday = DayType.valueOf(parts[3].trim()),
                    friday = DayType.valueOf(parts[4].trim()),
                    saturday = DayType.valueOf(parts[5].trim()),
                    sunday = DayType.valueOf(parts[6].trim()),
                )
            } catch (_: IllegalArgumentException) {
                WeeklyDayPattern()
            }
        }
    }
}
