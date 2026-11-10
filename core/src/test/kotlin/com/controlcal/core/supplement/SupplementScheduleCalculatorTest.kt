package com.controlcal.core.supplement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SupplementScheduleCalculatorTest {

    private val baseDate = LocalDate.of(2026, 11, 10) // Tuesday

    @Test
    fun `isDaySelectedInMask identifies active weekdays`() {
        // Mon, Wed, Fri: Mon=0, Wed=2, Fri=4 -> 1 + 4 + 16 = 21
        val mask = SupplementScheduleCalculator.createWeekdaysMask(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )
        assertEquals(21, mask)

        assertTrue(SupplementScheduleCalculator.isDaySelectedInMask(mask, DayOfWeek.MONDAY))
        assertFalse(SupplementScheduleCalculator.isDaySelectedInMask(mask, DayOfWeek.TUESDAY))
        assertTrue(SupplementScheduleCalculator.isDaySelectedInMask(mask, DayOfWeek.WEDNESDAY))
        assertFalse(SupplementScheduleCalculator.isDaySelectedInMask(mask, DayOfWeek.THURSDAY))
        assertTrue(SupplementScheduleCalculator.isDaySelectedInMask(mask, DayOfWeek.FRIDAY))
        assertFalse(SupplementScheduleCalculator.isDaySelectedInMask(mask, DayOfWeek.SATURDAY))
        assertFalse(SupplementScheduleCalculator.isDaySelectedInMask(mask, DayOfWeek.SUNDAY))
    }

    @Test
    fun `daily supplement is scheduled every day after start date`() {
        val supplement = Supplement(
            id = 1L,
            name = "Vitamina D3",
            doseAmount = 2000.0,
            doseUnit = "UI",
            scheduleType = ScheduleType.DAILY,
            startDate = baseDate,
            times = listOf(LocalTime.of(8, 0)),
        )

        assertFalse(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate.minusDays(1)))
        assertTrue(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate))
        assertTrue(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate.plusDays(1)))
        assertTrue(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate.plusDays(30)))
    }

    @Test
    fun `weekdays supplement only fires on selected days`() {
        // Monday (1) and Thursday (8) -> mask = 9
        val mask = SupplementScheduleCalculator.createWeekdaysMask(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val supplement = Supplement(
            id = 2L,
            name = "Hierro",
            doseAmount = 20.0,
            doseUnit = "mg",
            scheduleType = ScheduleType.WEEKDAYS,
            weekdays = mask,
            startDate = LocalDate.of(2026, 11, 9), // Monday
            times = listOf(LocalTime.of(9, 0)),
        )

        assertTrue(SupplementScheduleCalculator.isScheduledOn(supplement, LocalDate.of(2026, 11, 9))) // Mon
        assertFalse(SupplementScheduleCalculator.isScheduledOn(supplement, LocalDate.of(2026, 11, 10))) // Tue
        assertFalse(SupplementScheduleCalculator.isScheduledOn(supplement, LocalDate.of(2026, 11, 11))) // Wed
        assertTrue(SupplementScheduleCalculator.isScheduledOn(supplement, LocalDate.of(2026, 11, 12))) // Thu
        assertFalse(SupplementScheduleCalculator.isScheduledOn(supplement, LocalDate.of(2026, 11, 13))) // Fri
    }

    @Test
    fun `every N days supplement fires at exact intervals`() {
        // Every 3 days starting 2026-11-10
        val supplement = Supplement(
            id = 3L,
            name = "B12",
            doseAmount = 1000.0,
            doseUnit = "µg",
            scheduleType = ScheduleType.EVERY_N_DAYS,
            intervalDays = 3,
            startDate = baseDate,
            times = listOf(LocalTime.of(14, 0)),
        )

        assertTrue(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate)) // Day 0
        assertFalse(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate.plusDays(1))) // Day 1
        assertFalse(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate.plusDays(2))) // Day 2
        assertTrue(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate.plusDays(3))) // Day 3
        assertFalse(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate.plusDays(4))) // Day 4
        assertTrue(SupplementScheduleCalculator.isScheduledOn(supplement, baseDate.plusDays(6))) // Day 6
    }

    @Test
    fun `getScheduledDosesForDate builds pending and recorded doses`() {
        val supp1 = Supplement(
            id = 1L,
            name = "Magnesio",
            doseAmount = 400.0,
            doseUnit = "mg",
            scheduleType = ScheduleType.DAILY,
            startDate = baseDate,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(21, 0)),
        )
        val supp2 = Supplement(
            id = 2L,
            name = "Omega 3",
            doseAmount = 1000.0,
            doseUnit = "mg",
            scheduleType = ScheduleType.DAILY,
            startDate = baseDate,
            times = listOf(LocalTime.of(13, 0)),
        )

        val existingIntakes = listOf(
            SupplementIntake(
                id = 10L,
                supplementId = 1L,
                scheduledDate = baseDate,
                scheduledTime = LocalTime.of(8, 0),
                status = IntakeStatus.TAKEN,
            )
        )

        val doses = SupplementScheduleCalculator.getScheduledDosesForDate(
            supplements = listOf(supp1, supp2),
            date = baseDate,
            existingIntakes = existingIntakes,
        )

        assertEquals(3, doses.size)
        // 08:00 Magnesio (TAKEN)
        assertEquals(LocalTime.of(8, 0), doses[0].scheduledTime)
        assertEquals("Magnesio", doses[0].supplement.name)
        assertTrue(doses[0].isTaken)

        // 13:00 Omega 3 (PENDING)
        assertEquals(LocalTime.of(13, 0), doses[1].scheduledTime)
        assertEquals("Omega 3", doses[1].supplement.name)
        assertTrue(doses[1].isPending)

        // 21:00 Magnesio (PENDING)
        assertEquals(LocalTime.of(21, 0), doses[2].scheduledTime)
        assertEquals("Magnesio", doses[2].supplement.name)
        assertTrue(doses[2].isPending)
    }
}
