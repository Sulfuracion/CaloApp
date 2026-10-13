package com.controlcal.app.data.repo

import com.controlcal.app.data.db.dao.MealEntryDao
import com.controlcal.core.model.Meal
import com.controlcal.core.model.MealEntry
import com.controlcal.core.repo.MealEntryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealEntryRepositoryImpl @Inject constructor(
    private val mealEntryDao: MealEntryDao,
) : MealEntryRepository {

    override suspend fun insert(entry: MealEntry): Long =
        mealEntryDao.insert(entry.toEntity())

    override suspend fun update(entry: MealEntry) =
        mealEntryDao.update(entry.toEntity())

    override suspend fun delete(entry: MealEntry) =
        mealEntryDao.delete(entry.toEntity())

    override suspend fun getByDate(date: String): List<MealEntry> =
        mealEntryDao.entriesForDate(date).map { it.toDomain() }

    override suspend fun getByDateAndMeal(date: String, meal: Meal): List<MealEntry> =
        mealEntryDao.entriesForDateAndMeal(date, meal).map { it.toDomain() }

    override suspend fun getByProduct(productId: Long): List<MealEntry> =
        mealEntryDao.entriesForProduct(productId).map { it.toDomain() }
}
