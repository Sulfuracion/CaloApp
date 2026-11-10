package com.controlcal.core.supplement

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Calculates the next alarm instant/dose for a supplement.
 */
object NextAlarmCalculator {

    private const val MAX_LOOKAHEAD_DAYS = 366L

    /**
     * Finds the next scheduled date and time for [supplement] occurring strictly after [fromDateTime].
     * Returns null if the supplement is inactive, reminders are disabled, or no schedule matches.
     */
    fun findNextDose(
        supplement: Supplement,
        fromDateTime: LocalDateTime,
    ): Pair<LocalDate, LocalTime>? {
        if (!supplement.active || !supplement.remindersEnabled || supplement.times.isEmpty()) {
            return null
        }

        val fromDate = fromDateTime.toLocalDate()
        val fromTime = fromDateTime.toLocalTime()
        val startDate = if (fromDate.isBefore(supplement.startDate)) supplement.startDate else fromDate

        // Check if there is a remaining dose today (if today is on or after start date)
        if (startDate == fromDate && SupplementScheduleCalculator.isScheduledOn(supplement, fromDate)) {
            val futureTimesToday = supplement.times
                .filter { it.isAfter(fromTime) }
                .sorted()
            if (futureTimesToday.isNotEmpty()) {
                return Pair(fromDate, futureTimesToday.first())
            }
        }

        // Search forward starting from tomorrow or start_date
        val initialNextDate = if (startDate.isAfter(fromDate)) startDate else fromDate.plusDays(1)
        val sortedTimes = supplement.times.sorted()

        var candidateDate = initialNextDate
        val maxDate = fromDate.plusDays(MAX_LOOKAHEAD_DAYS)

        while (!candidateDate.isAfter(maxDate)) {
            if (SupplementScheduleCalculator.isScheduledOn(supplement, candidateDate)) {
                return Pair(candidateDate, sortedTimes.first())
            }
            candidateDate = candidateDate.plusDays(1)
        }

        return null
    }

    /**
     * Converts a scheduled date and time to epoch milliseconds in [zoneId],
     * properly accounting for daylight saving time (DST) shifts.
     */
    fun calculateTriggerEpochMillis(
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        zoneId: ZoneId,
    ): Long {
        return scheduledDate.atTime(scheduledTime).atZone(zoneId).toInstant().toEpochMilli()
    }
}
