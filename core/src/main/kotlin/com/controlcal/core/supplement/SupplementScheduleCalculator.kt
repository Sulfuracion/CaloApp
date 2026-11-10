package com.controlcal.core.supplement

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure domain logic for evaluating supplement schedules and generating scheduled doses.
 */
object SupplementScheduleCalculator {

    /**
     * Checks if [dayOfWeek] is set in [weekdaysMask].
     * Monday = bit 0 (value 1), ..., Sunday = bit 6 (value 64).
     */
    fun isDaySelectedInMask(weekdaysMask: Int?, dayOfWeek: DayOfWeek): Boolean {
        if (weekdaysMask == null) return false
        val bitIndex = dayOfWeek.value - 1
        return (weekdaysMask and (1 shl bitIndex)) != 0
    }

    /**
     * Creates a bitmask integer for a collection of [DayOfWeek].
     */
    fun createWeekdaysMask(days: Iterable<DayOfWeek>): Int {
        var mask = 0
        for (day in days) {
            val bitIndex = day.value - 1
            mask = mask or (1 shl bitIndex)
        }
        return mask
    }

    /**
     * Checks whether [supplement] is scheduled on [date].
     */
    fun isScheduledOn(supplement: Supplement, date: LocalDate): Boolean {
        if (date.isBefore(supplement.startDate)) return false
        if (supplement.times.isEmpty()) return false

        return when (supplement.scheduleType) {
            ScheduleType.DAILY -> true
            ScheduleType.WEEKDAYS -> isDaySelectedInMask(supplement.weekdays, date.dayOfWeek)
            ScheduleType.EVERY_N_DAYS -> {
                val interval = supplement.intervalDays
                if (interval == null || interval <= 0) {
                    false
                } else {
                    val daysBetween = ChronoUnit.DAYS.between(supplement.startDate, date)
                    daysBetween >= 0 && (daysBetween % interval == 0L)
                }
            }
        }
    }

    /**
     * Builds the complete list of [ScheduledDose] for [date].
     * Includes all active supplements scheduled for this date, plus any supplement
     * (even if inactive or unscheduled) that has a recorded intake on this date.
     */
    fun getScheduledDosesForDate(
        supplements: List<Supplement>,
        date: LocalDate,
        existingIntakes: List<SupplementIntake>,
    ): List<ScheduledDose> {
        val intakesBySupplementAndTime = existingIntakes.associateBy { intake ->
            Pair(intake.supplementId, intake.scheduledTime)
        }

        val doses = mutableListOf<ScheduledDose>()
        for (supplement in supplements) {
            val isScheduled = isScheduledOn(supplement, date)
            if (supplement.active && isScheduled) {
                for (time in supplement.times) {
                    val intake = intakesBySupplementAndTime[Pair(supplement.id, time)]
                    doses.add(
                        ScheduledDose(
                            supplement = supplement,
                            scheduledDate = date,
                            scheduledTime = time,
                            intake = intake,
                        )
                    )
                }
            } else {
                // If inactive or not scheduled, show doses only if an intake was already recorded
                for (time in supplement.times) {
                    val intake = intakesBySupplementAndTime[Pair(supplement.id, time)]
                    if (intake != null) {
                        doses.add(
                            ScheduledDose(
                                supplement = supplement,
                                scheduledDate = date,
                                scheduledTime = time,
                                intake = intake,
                            )
                        )
                    }
                }
            }
        }

        return doses.sortedWith(
            compareBy<ScheduledDose> { it.scheduledTime }
                .thenBy { it.supplement.name }
        )
    }
}
