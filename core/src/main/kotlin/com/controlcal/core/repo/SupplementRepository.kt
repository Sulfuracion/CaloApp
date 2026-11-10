package com.controlcal.core.repo

import com.controlcal.core.supplement.IntakeStatus
import com.controlcal.core.supplement.Supplement
import com.controlcal.core.supplement.SupplementIntake
import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure repository interface for managing supplements and intake logs.
 */
interface SupplementRepository {

    suspend fun getAllSupplements(): List<Supplement>

    suspend fun getActiveSupplements(): List<Supplement>

    suspend fun getSupplementById(id: Long): Supplement?

    suspend fun saveSupplement(supplement: Supplement): Long

    suspend fun updateSupplement(supplement: Supplement)

    suspend fun setSupplementActive(id: Long, active: Boolean)

    suspend fun deleteSupplement(id: Long)

    suspend fun getIntakesForDate(date: LocalDate): List<SupplementIntake>

    suspend fun getIntakesBetween(startDate: LocalDate, endDate: LocalDate): List<SupplementIntake>

    /**
     * Records or updates a dose intake.
     * Returns true if newly created or updated, false if already recorded with same status.
     */
    suspend fun recordIntake(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        status: IntakeStatus,
    ): Boolean

    /**
     * Deletes an intake record, effectively reverting the dose to PENDING status.
     */
    suspend fun deleteIntake(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
    )
}
