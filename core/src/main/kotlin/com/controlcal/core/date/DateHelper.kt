package com.controlcal.core.date

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure Kotlin/JVM date utilities for ControlCal according to Task M.1:
 * - Conversions between [LocalDate] and epoch millis use strictly [ZoneOffset.UTC].
 *   This avoids date-shifting bugs when devices are in non-UTC or daylight saving timezones.
 * - Current date ("today") calculation uses an injectable [Clock] in the device's local timezone,
 *   subsequently converted to UTC midnight millis for calendar validation without blocking
 *   today during early morning hours in daylight saving time.
 * - Date formatting uses patterns loaded from strings.xml with full month and day names
 *   without abbreviations to ensure identical output across JVM and Android.
 */
object DateHelper {

    /**
     * Converts a [LocalDate] to midnight UTC milliseconds.
     * Material 3 DatePicker represents selected days as UTC midnight millis.
     */
    fun localDateToUtcMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /**
     * Converts UTC midnight milliseconds back to [LocalDate] using strictly [ZoneOffset.UTC].
     * Never converts using local device zone, preventing shifts like yesterday in negative offsets
     * or Canary Islands daylight saving time.
     */
    fun utcMillisToLocalDate(utcMillis: Long): LocalDate {
        return Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).toLocalDate()
    }

    /**
     * Returns today's [LocalDate] in the timezone of the given [clock].
     */
    fun getToday(clock: Clock = Clock.systemDefaultZone()): LocalDate {
        return LocalDate.now(clock)
    }

    /**
     * Returns today's date in local timezone converted to UTC midnight milliseconds.
     * Use this as the upper bound for selectable dates in DatePicker to ensure
     * today is selectable even between 00:00 and 01:00/02:00 in daylight saving time.
     */
    fun getTodayUtcMillis(clock: Clock = Clock.systemDefaultZone()): Long {
        return localDateToUtcMillis(getToday(clock))
    }

    /**
     * Checks if [utcMillis] represents a date that is not in the future
     * relative to today in the given [clock].
     */
    fun isNotFuture(utcMillis: Long, clock: Clock = Clock.systemDefaultZone()): Boolean {
        return utcMillis <= getTodayUtcMillis(clock)
    }

    /**
     * Formats a [date] according to [patternSameYear] if [date.year] matches [currentYear],
     * or [patternOtherYear] otherwise, using Spanish locale.
     */
    fun formatDate(
        date: LocalDate,
        currentYear: Int,
        patternSameYear: String,
        patternOtherYear: String,
        locale: Locale = Locale.forLanguageTag("es-ES"),
    ): String {
        val pattern = if (date.year == currentYear) patternSameYear else patternOtherYear
        val formatter = DateTimeFormatter.ofPattern(pattern, locale)
        return date.format(formatter)
    }
}
