package com.controlcal.core.date

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DateHelperTest {

    @Test
    fun roundTrip_convertsBetweenLocalDateAndUtcMillisPreservingExactDate() {
        val testDates = listOf(
            LocalDate.of(2020, 2, 29), // Leap year
            LocalDate.of(2024, 2, 29), // Leap year
            LocalDate.of(2026, 1, 1),   // Year boundary
            LocalDate.of(2026, 10, 14), // Current date
            LocalDate.of(2026, 10, 15),
            LocalDate.of(2026, 12, 31), // Year boundary
            LocalDate.of(1999, 12, 31), // Past century
            LocalDate.of(2050, 7, 7),   // Future date
        )

        for (date in testDates) {
            val millis = DateHelper.localDateToUtcMillis(date)
            val convertedBack = DateHelper.utcMillisToLocalDate(millis)
            assertEquals("Round trip failed for $date", date, convertedBack)

            val millisAgain = DateHelper.localDateToUtcMillis(convertedBack)
            assertEquals("Millis round trip failed for $date", millis, millisAgain)
        }
    }

    @Test
    fun atEarlyMorningSummerTime_todayIsSelectableAndNotBlocked() {
        // Atlantic/Canary is in WEST (UTC+1) during summer daylight saving time.
        // At 00:30 local time on 2026-07-15, UTC time is 2026-07-14 23:30:00Z (still yesterday in UTC!).
        val canaryZone = ZoneId.of("Atlantic/Canary")
        val localDateTime = ZonedDateTime.of(2026, 7, 15, 0, 30, 0, 0, canaryZone)
        val fixedClock = Clock.fixed(localDateTime.toInstant(), canaryZone)

        // 1. Verify getToday returns 2026-07-15 (local time)
        val today = DateHelper.getToday(fixedClock)
        assertEquals(LocalDate.of(2026, 7, 15), today)

        // 2. Calendar representation of today in UTC midnight millis
        val todayUtcMillis = DateHelper.getTodayUtcMillis(fixedClock)
        assertEquals(DateHelper.localDateToUtcMillis(today), todayUtcMillis)

        // 3. Show that comparing with raw currentTimeMillis / clock.millis would erroneously BLOCK today:
        val rawCurrentTimeMillis = fixedClock.millis()
        assertTrue(
            "Raw current time is in the previous day in UTC",
            todayUtcMillis > rawCurrentTimeMillis
        )
        // With raw currentTimeMillis, todayUtcMillis <= rawCurrentTimeMillis would be FALSE (bug!)

        // 4. Verify DateHelper.isNotFuture correctly allows today:
        assertTrue(
            "Today must be selectable at 00:30 in daylight saving time",
            DateHelper.isNotFuture(todayUtcMillis, fixedClock)
        )

        // 5. Verify tomorrow (2026-07-16) is blocked:
        val tomorrow = today.plusDays(1)
        val tomorrowUtcMillis = DateHelper.localDateToUtcMillis(tomorrow)
        assertFalse(
            "Future date must not be selectable",
            DateHelper.isNotFuture(tomorrowUtcMillis, fixedClock)
        )

        // 6. Verify yesterday (2026-07-14) is selectable:
        val yesterday = today.minusDays(1)
        val yesterdayUtcMillis = DateHelper.localDateToUtcMillis(yesterday)
        assertTrue(
            "Past date must be selectable",
            DateHelper.isNotFuture(yesterdayUtcMillis, fixedClock)
        )
    }

    @Test
    fun formatDate_withFullPatterns_formatsSpanishWithoutAbbreviations() {
        val sameYearPattern = "EEEE, d 'de' MMMM"
        val otherYearPattern = "EEEE, d 'de' MMMM 'de' yyyy"

        val dateCurrentYear = LocalDate.of(2026, 10, 14) // Wednesday
        val formattedCurrent = DateHelper.formatDate(
            date = dateCurrentYear,
            currentYear = 2026,
            patternSameYear = sameYearPattern,
            patternOtherYear = otherYearPattern,
        )
        assertEquals("miércoles, 14 de octubre", formattedCurrent)

        val dateOtherYear = LocalDate.of(2025, 10, 14) // Tuesday
        val formattedOther = DateHelper.formatDate(
            date = dateOtherYear,
            currentYear = 2026,
            patternSameYear = sameYearPattern,
            patternOtherYear = otherYearPattern,
        )
        assertEquals("martes, 14 de octubre de 2025", formattedOther)
    }

    @Test
    fun formatDate_withNavPatterns_formatsWithoutWeekday() {
        val navSameYearPattern = "d 'de' MMMM"
        val navOtherYearPattern = "d 'de' MMMM 'de' yyyy"

        val dateCurrentYear = LocalDate.of(2026, 10, 14)
        val formattedCurrent = DateHelper.formatDate(
            date = dateCurrentYear,
            currentYear = 2026,
            patternSameYear = navSameYearPattern,
            patternOtherYear = navOtherYearPattern,
        )
        assertEquals("14 de octubre", formattedCurrent)

        val dateOtherYear = LocalDate.of(2025, 10, 14)
        val formattedOther = DateHelper.formatDate(
            date = dateOtherYear,
            currentYear = 2026,
            patternSameYear = navSameYearPattern,
            patternOtherYear = navOtherYearPattern,
        )
        assertEquals("14 de octubre de 2025", formattedOther)
    }
}
