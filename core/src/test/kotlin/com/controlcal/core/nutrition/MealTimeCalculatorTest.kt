package com.controlcal.core.nutrition

import com.controlcal.core.model.MealEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class MealTimeCalculatorTest {

    private val madridZone = ZoneId.of("Europe/Madrid")

    private fun fixedClock(dateTimeIso: String, zoneId: ZoneId = madridZone): Clock {
        val instant = Instant.parse(dateTimeIso)
        return Clock.fixed(instant, zoneId)
    }

    private fun entryWithLoggedAt(id: Long, date: String, meal: String, loggedAt: Long): MealEntry {
        return MealEntry(
            id = id,
            date = date,
            meal = meal,
            productId = 1L,
            productName = "Sample",
            grams = 100.0,
            kcal = 100.0,
            protein = 10.0,
            carbs = 10.0,
            fat = 2.0,
            loggedAt = loggedAt,
        )
    }

    @Test
    fun resolveMealTime_whenEntriesExist_returnsEarliestEntryTime() {
        val date = LocalDate.of(2026, 10, 15)
        // 08:30 in Madrid (UTC+2) -> 06:30 UTC
        val millis830 = date.atTime(LocalTime.of(8, 30)).atZone(madridZone).toInstant().toEpochMilli()
        // 09:15 in Madrid (UTC+2) -> 07:15 UTC
        val millis915 = date.atTime(LocalTime.of(9, 15)).atZone(madridZone).toInstant().toEpochMilli()

        val entries = listOf(
            entryWithLoggedAt(1L, "2026-10-15", "BREAKFAST", millis915),
            entryWithLoggedAt(2L, "2026-10-15", "BREAKFAST", millis830),
        )

        val resolved = MealTimeCalculator.resolveMealTime(
            date = date,
            existingEntries = entries,
            configuredStartTime = LocalTime.of(8, 0),
            zoneId = madridZone,
        )

        assertEquals(LocalTime.of(8, 30), resolved)
    }

    @Test
    fun resolveMealTime_whenNoEntriesAndToday_returnsCurrentTime() {
        val today = LocalDate.of(2026, 10, 15)
        // 13:45 Madrid time = 11:45 UTC
        val clock = fixedClock("2026-10-15T11:45:00Z", madridZone)

        val resolved = MealTimeCalculator.resolveMealTime(
            date = today,
            existingEntries = emptyList(),
            configuredStartTime = LocalTime.of(14, 0),
            zoneId = madridZone,
            clock = clock,
        )

        assertEquals(LocalTime.of(13, 45), resolved)
    }

    @Test
    fun resolveMealTime_whenNoEntriesAndAnotherDay_returnsConfiguredStartTime() {
        val clock = fixedClock("2026-10-15T10:00:00Z", madridZone)
        val yesterday = LocalDate.of(2026, 10, 14)

        val resolved = MealTimeCalculator.resolveMealTime(
            date = yesterday,
            existingEntries = emptyList(),
            configuredStartTime = LocalTime.of(14, 30),
            zoneId = madridZone,
            clock = clock,
        )

        assertEquals(LocalTime.of(14, 30), resolved)
    }

    @Test
    fun calculateLoggedAt_alwaysFallsStrictlyWithinDate() {
        val date = LocalDate.of(2026, 10, 15)
        val time = LocalTime.of(23, 59)

        val loggedAt = MealTimeCalculator.calculateLoggedAt(date, time, madridZone)
        val convertedBack = Instant.ofEpochMilli(loggedAt).atZone(madridZone).toLocalDate()

        assertEquals(date, convertedBack)
    }

    @Test
    fun marchDaylightSavingTimeChange_alwaysFallsStrictlyWithinDay() {
        // In Europe/Madrid, DST starts on the last Sunday of March (2026-03-29).
        // At 02:00, clocks turn forward to 03:00 (UTC+1 to UTC+2).
        val dstDate = LocalDate.of(2026, 3, 29)

        // 1. Regular breakfast at 08:30 on DST day
        val breakfastLoggedAt = MealTimeCalculator.calculateLoggedAt(dstDate, LocalTime.of(8, 30), madridZone)
        val breakfastDate = Instant.ofEpochMilli(breakfastLoggedAt).atZone(madridZone).toLocalDate()
        assertEquals(dstDate, breakfastDate)
        assertEquals(LocalTime.of(8, 30), Instant.ofEpochMilli(breakfastLoggedAt).atZone(madridZone).toLocalTime())

        // 2. Early morning before transition (01:30 CET = UTC+1)
        val earlyLoggedAt = MealTimeCalculator.calculateLoggedAt(dstDate, LocalTime.of(1, 30), madridZone)
        assertEquals(dstDate, Instant.ofEpochMilli(earlyLoggedAt).atZone(madridZone).toLocalDate())

        // 3. Gap time (02:30 does not exist in standard CET, Java adjusts to 03:30 CEST)
        val gapLoggedAt = MealTimeCalculator.calculateLoggedAt(dstDate, LocalTime.of(2, 30), madridZone)
        assertEquals(dstDate, Instant.ofEpochMilli(gapLoggedAt).atZone(madridZone).toLocalDate())

        // 4. End of the DST day (23:45 CEST)
        val lateLoggedAt = MealTimeCalculator.calculateLoggedAt(dstDate, LocalTime.of(23, 45), madridZone)
        assertEquals(dstDate, Instant.ofEpochMilli(lateLoggedAt).atZone(madridZone).toLocalDate())
    }

    @Test
    fun shiftToDate_copyFromPreviousDay_preservesLocalTimeAndSetsTargetDate() {
        // Original entry on 2026-10-14 at 14:15 Madrid time
        val sourceDate = LocalDate.of(2026, 10, 14)
        val originalTime = LocalTime.of(14, 15)
        val sourceLoggedAt = sourceDate.atTime(originalTime).atZone(madridZone).toInstant().toEpochMilli()

        val targetDate = LocalDate.of(2026, 10, 15)
        val newLoggedAt = MealTimeCalculator.shiftToDate(sourceLoggedAt, targetDate, madridZone)

        val targetZoned = Instant.ofEpochMilli(newLoggedAt).atZone(madridZone)
        assertEquals(targetDate, targetZoned.toLocalDate())
        assertEquals(originalTime, targetZoned.toLocalTime())
    }

    @Test
    fun shiftToDate_copyAcrossMarchDstTransition_preservesLocalTimeOnDstDate() {
        // 2026-03-28 (Saturday before DST, UTC+1) at 13:00
        val sourceDate = LocalDate.of(2026, 3, 28)
        val lunchTime = LocalTime.of(13, 0)
        val sourceLoggedAt = sourceDate.atTime(lunchTime).atZone(madridZone).toInstant().toEpochMilli()

        // Target: 2026-03-29 (Sunday with DST change, UTC+2)
        val targetDate = LocalDate.of(2026, 3, 29)
        val newLoggedAt = MealTimeCalculator.shiftToDate(sourceLoggedAt, targetDate, madridZone)

        val targetZoned = Instant.ofEpochMilli(newLoggedAt).atZone(madridZone)
        assertEquals(targetDate, targetZoned.toLocalDate())
        assertEquals(lunchTime, targetZoned.toLocalTime())
    }
}
