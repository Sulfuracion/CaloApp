package com.controlcal.app.data.repo

import com.controlcal.app.data.db.dao.WeightEntryDao
import com.controlcal.core.model.WeightEntry
import com.controlcal.core.repo.WeightEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightEntryRepositoryImpl @Inject constructor(
    private val weightEntryDao: WeightEntryDao,
) : WeightEntryRepository {

    override suspend fun upsert(entry: WeightEntry) {
        weightEntryDao.upsert(entry.toEntity())
    }

    override suspend fun getByDate(date: String): WeightEntry? =
        weightEntryDao.getByDate(date)?.toDomain()

    override suspend fun getBetween(from: String, to: String): List<WeightEntry> =
        weightEntryDao.entriesBetween(from, to).map { it.toDomain() }

    override suspend fun getAll(): List<WeightEntry> =
        weightEntryDao.getAllAsc().map { it.toDomain() }

    override suspend fun deleteByDate(date: String) {
        weightEntryDao.deleteByDate(date)
    }

    override suspend fun deleteAll() {
        weightEntryDao.deleteAll()
    }

    override suspend fun moveEntry(oldDate: String, newEntry: WeightEntry) {
        weightEntryDao.moveEntry(oldDate, newEntry.toEntity())
    }

    fun observeAllDesc(): Flow<List<WeightEntry>> =
        weightEntryDao.observeAllDesc().map { list -> list.map { it.toDomain() } }
}
