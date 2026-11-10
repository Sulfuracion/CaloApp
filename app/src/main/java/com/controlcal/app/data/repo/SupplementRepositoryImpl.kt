package com.controlcal.app.data.repo

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.controlcal.app.data.db.ControlCalDatabase
import com.controlcal.app.data.db.dao.SupplementDao
import com.controlcal.app.data.db.entity.SupplementEntity
import com.controlcal.app.data.db.entity.SupplementIntakeEntity
import com.controlcal.core.repo.SupplementRepository
import com.controlcal.core.supplement.IntakeStatus
import com.controlcal.core.supplement.Supplement
import com.controlcal.core.supplement.SupplementIntake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplementRepositoryImpl @Inject constructor(
    private val database: ControlCalDatabase,
    private val supplementDao: SupplementDao,
) : SupplementRepository {

    fun observeAllSupplements(): Flow<List<Supplement>> =
        supplementDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    fun observeActiveSupplements(): Flow<List<Supplement>> =
        supplementDao.getActiveFlow().map { list -> list.map { it.toDomain() } }

    fun observeIntakesForDate(date: LocalDate): Flow<List<SupplementIntake>> =
        supplementDao.getIntakesForDateFlow(date.toString()).map { list -> list.map { it.toDomain() } }

    override suspend fun getAllSupplements(): List<Supplement> =
        supplementDao.getAll().map { it.toDomain() }

    override suspend fun getActiveSupplements(): List<Supplement> =
        supplementDao.getActive().map { it.toDomain() }

    override suspend fun getSupplementById(id: Long): Supplement? =
        supplementDao.getById(id)?.toDomain()

    override suspend fun saveSupplement(supplement: Supplement): Long {
        val entity = SupplementEntity.fromDomain(supplement)
        return supplementDao.insert(entity)
    }

    override suspend fun updateSupplement(supplement: Supplement) {
        val entity = SupplementEntity.fromDomain(supplement.copy(updatedAt = System.currentTimeMillis()))
        supplementDao.update(entity)
    }

    override suspend fun setSupplementActive(id: Long, active: Boolean) {
        supplementDao.setActive(id, active)
    }

    override suspend fun deleteSupplement(id: Long) {
        supplementDao.deleteById(id)
    }

    override suspend fun getIntakesForDate(date: LocalDate): List<SupplementIntake> =
        supplementDao.getIntakesForDate(date.toString()).map { it.toDomain() }

    override suspend fun getIntakesBetween(startDate: LocalDate, endDate: LocalDate): List<SupplementIntake> =
        supplementDao.getIntakesBetween(startDate.toString(), endDate.toString()).map { it.toDomain() }

    override suspend fun recordIntake(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        status: IntakeStatus,
    ): Boolean {
        val dateStr = scheduledDate.toString()
        val timeStr = scheduledTime.toString()
        val entity = SupplementIntakeEntity(
            supplementId = supplementId,
            scheduledDate = dateStr,
            scheduledTime = timeStr,
            status = status.name,
            recordedAt = System.currentTimeMillis(),
        )
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val existing = supplementDao.getIntake(supplementId, dateStr, timeStr)
                if (existing != null) {
                    if (existing.status == status.name) {
                        false
                    } else {
                        supplementDao.insertIntake(entity.copy(id = existing.id))
                        true
                    }
                } else {
                    supplementDao.insertIntake(entity)
                    true
                }
            }
        }
    }

    override suspend fun deleteIntake(
        supplementId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
    ) {
        supplementDao.deleteIntake(supplementId, scheduledDate.toString(), scheduledTime.toString())
    }
}
