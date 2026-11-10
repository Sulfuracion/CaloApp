package com.controlcal.core.supplement

import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain representation of a supplement configuration.
 *
 * @param weekdays Bitmask for weekdays (Monday = bit 0, ..., Sunday = bit 6).
 */
data class Supplement(
    val id: Long = 0L,
    val name: String,
    val doseAmount: Double,
    val doseUnit: String,
    val scheduleType: ScheduleType,
    val weekdays: Int? = null,
    val intervalDays: Int? = null,
    val startDate: LocalDate,
    val times: List<LocalTime>,
    val active: Boolean = true,
    val remindersEnabled: Boolean = true,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
