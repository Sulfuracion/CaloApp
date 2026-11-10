package com.controlcal.core.supplement

import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain representation of a supplement dose intake entry.
 * Note: Absence of record indicates a pending dose.
 */
data class SupplementIntake(
    val id: Long = 0L,
    val supplementId: Long,
    val scheduledDate: LocalDate,
    val scheduledTime: LocalTime,
    val status: IntakeStatus,
    val recordedAt: Long = System.currentTimeMillis(),
)
