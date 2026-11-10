package com.controlcal.core.supplement

import java.time.LocalDate
import java.time.LocalTime

/**
 * Representation of a scheduled dose for a given date and time.
 * If [intake] is null, the dose is pending (not yet taken or skipped).
 */
data class ScheduledDose(
    val supplement: Supplement,
    val scheduledDate: LocalDate,
    val scheduledTime: LocalTime,
    val intake: SupplementIntake?,
) {
    val isPending: Boolean get() = intake == null
    val isTaken: Boolean get() = intake?.status == IntakeStatus.TAKEN
    val isSkipped: Boolean get() = intake?.status == IntakeStatus.SKIPPED
}
