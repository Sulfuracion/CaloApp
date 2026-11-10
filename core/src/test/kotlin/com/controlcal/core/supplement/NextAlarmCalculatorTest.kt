package com.controlcal.core.supplement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class NextAlarmCalculatorTest {

    private val baseDate = LocalDate.of(2026, 11, 10) // Tuesday

    @Test
    fun `findNextDose returns later time on same day`() {
        val supplement = Supplement(
            id = 1L,
            name = "Vitamina C",
            doseAmount = 500.0,
            doseUnit = "mg",
            scheduleType = ScheduleType.DAILY,
            startDate = baseDate,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0)),
        )

        // It is 10:00 -> next dose should be 14:00 today
        val next = NextAlarmCalculator.findNextDose(
            supplement = supplement,
            fromDateTime = LocalDateTime.of(baseDate, LocalTime.of(10, 0)),
        )

        assertNotNull(next)
        assertEquals(baseDate, next!!.first)
        assertEquals(LocalTime.of(14, 0), next.second)
    }

    @Test
    fun `findNextDose rolls over to tomorrow if past all times today`() {
        val supplement = Supplement(
            id = 1L,
            name = "Vitamina C",
            doseAmount = 500.0,
            doseUnit = "mg",
            scheduleType = ScheduleType.DAILY,
            startDate = baseDate,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        )

        // It is 21:00 -> next dose is tomorrow at 08:00
        val next = NextAlarmCalculator.findNextDose(
            supplement = supplement,
            fromDateTime = LocalDateTime.of(baseDate, LocalTime.of(21, 0)),
        )

        assertNotNull(next)
        assertEquals(baseDate.plusDays(1), next!!.first)
        assertEquals(LocalTime.of(8, 0), next.second)
    }

    @Test
    fun `findNextDose respects weekday schedule over the weekend`() {
        // Only Monday and Friday
        val mask = SupplementScheduleCalculator.createWeekdaysMask(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val supplement = Supplement(
            id = 2L,
            name = "Creatina",
            doseAmount = 5.0,
            doseUnit = "g",
            scheduleType = ScheduleType.WEEKDAYS,
            weekdays = mask,
            startDate = LocalDate.of(2026, 11, 6), // Friday
            times = listOf(LocalTime.of(9, 0)),
        )

        // It is Friday Nov 6 at 10:00 -> next dose should be Monday Nov 9 at 09:00
        val next = NextAlarmCalculator.findNextDose(
            supplement = supplement,
            fromDateTime = LocalDateTime.of(LocalDate.of(2026, 11, 6), LocalTime.of(10, 0)),
        )

        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 11, 9), next!!.first)
        assertEquals(LocalTime.of(9, 0), next.second)
    }

    @Test
    fun `findNextDose returns null when reminders disabled or inactive`() {
        val inactiveSupplement = Supplement(
            id = 1L,
            name = "Inactivo",
            doseAmount = 100.0,
            doseUnit = "mg",
            scheduleType = ScheduleType.DAILY,
            startDate = baseDate,
            times = listOf(LocalTime.of(8, 0)),
            active = false,
        )

        assertNull(
            NextAlarmCalculator.findNextDose(
                supplement = inactiveSupplement,
                fromDateTime = LocalDateTime.of(baseDate, LocalTime.of(7, 0)),
            )
        )

        val noRemindersSupplement = inactiveSupplement.copy(active = true, remindersEnabled = false)
        assertNull(
            NextAlarmCalculator.findNextDose(
                supplement = noRemindersSupplement,
                fromDateTime = LocalDateTime.of(baseDate, LocalTime.of(7, 0)),
            )
        )
    }

    @Test
    fun `calculateTriggerEpochMillis accounts for daylight saving time change`() {
        // Europe/Madrid springs forward on 2026-03-29 at 02:00 -> 03:00 (UTC+1 to UTC+2)
        val zone = ZoneId.of("Europe/Madrid")
        val beforeDst = LocalDate.of(2026, 3, 28) // UTC+1
        val afterDst = LocalDate.of(2026, 3, 30) // UTC+2

        val millisBefore = NextAlarmCalculator.calculateTriggerEpochMillis(beforeDst, LocalTime.of(10, 0), zone)
        val millisAfter = NextAlarmCalculator.calculateTriggerEpochMillis(afterDst, LocalTime.of(10, 0), zone)

        // 48 hours would normally be 48 * 3600 * 1000 = 172,800,000 ms.
        // Due to the lost hour on March 29, the elapsed time is 47 hours = 169,200,000 ms.
        val diff = millisAfter - millisBefore
        assertEquals(47 * 3600 * 1000L, diff)
    }
}
