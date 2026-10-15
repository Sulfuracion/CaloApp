package com.controlcal.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class WeeklyDayPatternTest {

    @Test
    fun defaultPatternHasExpectedSchedule() {
        val pattern = WeeklyDayPattern()

        assertEquals(DayType.TRAINING, pattern.dayTypeFor(DayOfWeek.MONDAY))
        assertEquals(DayType.REST, pattern.dayTypeFor(DayOfWeek.TUESDAY))
        assertEquals(DayType.TRAINING, pattern.dayTypeFor(DayOfWeek.WEDNESDAY))
        assertEquals(DayType.REST, pattern.dayTypeFor(DayOfWeek.THURSDAY))
        assertEquals(DayType.TRAINING, pattern.dayTypeFor(DayOfWeek.FRIDAY))
        assertEquals(DayType.REST, pattern.dayTypeFor(DayOfWeek.SATURDAY))
        assertEquals(DayType.REST, pattern.dayTypeFor(DayOfWeek.SUNDAY))
    }

    @Test
    fun withDayTypeUpdatesSingleDay() {
        val pattern = WeeklyDayPattern()
        val updated = pattern.withDayType(DayOfWeek.TUESDAY, DayType.TRAINING)
            .withDayType(DayOfWeek.FRIDAY, DayType.REST)

        assertEquals(DayType.TRAINING, updated.dayTypeFor(DayOfWeek.TUESDAY))
        assertEquals(DayType.REST, updated.dayTypeFor(DayOfWeek.FRIDAY))
        // Others unchanged
        assertEquals(DayType.TRAINING, updated.dayTypeFor(DayOfWeek.MONDAY))
        assertEquals(DayType.REST, updated.dayTypeFor(DayOfWeek.SUNDAY))
    }
}
