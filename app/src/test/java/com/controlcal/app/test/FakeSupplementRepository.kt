package com.controlcal.app.test

import com.controlcal.core.repo.SupplementRepository
import com.controlcal.core.supplement.IntakeStatus
import com.controlcal.core.supplement.Supplement
import com.controlcal.core.supplement.SupplementIntake
import java.time.LocalDate
import java.time.LocalTime

class FakeSupplementRepository : SupplementRepository {

    val supplements = mutableListOf<Supplement>()
    val intakes = mutableListOf<SupplementIntake>()
    private var nextSupplementId = 1L
    private var nextIntakeId = 1L

    override suspend fun getAllSupplements(): List<Supplement> = supplements.toList()

    override suspend fun getActiveSupplements(): List<Supplement> =
        supplements.filter { it.active }

    override suspend fun getSupplementById(id: Long): Supplement? =
        supplements.find { it.id == id }

    override suspend fun saveSupplement(supplement: Supplement): Long {
        val id = if (supplement.id <= 0) nextSupplementId++ else supplement.id
        val saved = supplement.copy(id = id)
        supplements.removeAll { it.id == id }
        supplements.add(saved)
        return id
    }

    override suspend fun updateSupplement(supplement: Supplement) {
        supplements.removeAll { it.id == supplement.id }
        supplements.add(supplement)
    }

    override suspend fun setSupplementActive(id: Long, active: Boolean) {
        val idx = supplements.indexOfFirst { it.id == id }
        if (idx != -1) {
            supplements[idx] = supplements[idx].copy(active = active)
        }
    }

    override suspend fun deleteSupplement(id: Long) {
        supplements.removeAll { it.id == id }
        intakes.removeAll { it.supplementId == id }
    }

    override suspend fun getIntakesForDate(date: LocalDate): List<SupplementIntake> =
        intakes.filter { it.scheduledDate == date }

    override suspend fun getIntakesBetween(startDate: LocalDate, endDate: LocalDate): List<SupplementIntake> =
        intakes.filter { !it.scheduledDate.isBefore(startDate) && !it.scheduledDate.isAfter(endDate) }

    override suspend fun recordIntake(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        status: IntakeStatus,
    ): Boolean {
        intakes.removeAll {
            it.supplementId == supplementId &&
                it.scheduledDate == scheduledDate &&
                it.scheduledTime == scheduledTime
        }
        intakes.add(
            SupplementIntake(
                id = nextIntakeId++,
                supplementId = supplementId,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                status = status,
                recordedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    override suspend fun deleteIntake(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
    ) {
        intakes.removeAll {
            it.supplementId == supplementId &&
                it.scheduledDate == scheduledDate &&
                it.scheduledTime == scheduledTime
        }
    }
}
