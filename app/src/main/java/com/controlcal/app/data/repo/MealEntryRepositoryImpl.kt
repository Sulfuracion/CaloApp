package com.controlcal.app.data.repo

import com.controlcal.app.data.db.dao.DailyLogDao
import com.controlcal.app.data.db.dao.MealEntryDao
import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.core.model.DayType
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.UserSettingsRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealEntryRepositoryImpl @Inject constructor(
    private val mealEntryDao: MealEntryDao,
    private val dailyLogDao: DailyLogDao,
    private val userSettingsRepository: UserSettingsRepository = object : UserSettingsRepository {
        override suspend fun getWeeklyPattern(): WeeklyDayPattern = WeeklyDayPattern()
        override suspend fun setWeeklyPattern(pattern: WeeklyDayPattern) {}
        override suspend fun getMinKcalLimit(): Double = 1200.0
        override suspend fun setMinKcalLimit(minKcal: Double) {}
        override suspend fun getLastTdeeProposalReviewedDate(): String? = null
        override suspend fun setLastTdeeProposalReviewedDate(date: String) {}
    },
) : MealEntryRepository {

    private suspend fun ensureDailyLogExists(date: String) {
        if (dailyLogDao.getByDate(date) == null) {
            val dayType = try {
                val localDate = LocalDate.parse(date)
                userSettingsRepository.getWeeklyPattern().dayTypeFor(localDate.dayOfWeek)
            } catch (_: Exception) {
                DayType.REST
            }
            dailyLogDao.insert(DailyLogEntity(date = date, dayType = dayType))
        }
    }

    override suspend fun insert(entry: MealEntry): Long {
        ensureDailyLogExists(entry.date)
        return mealEntryDao.insert(entry.toEntity())
    }

    override suspend fun insertAll(entries: List<MealEntry>): List<Long> {
        if (entries.isEmpty()) return emptyList()
        val distinctDates = entries.map { it.date }.distinct()
        for (date in distinctDates) {
            ensureDailyLogExists(date)
        }
        return mealEntryDao.insertAll(entries.map { it.toEntity() })
    }

    override suspend fun update(entry: MealEntry) {
        ensureDailyLogExists(entry.date)
        mealEntryDao.update(entry.toEntity())
    }

    override suspend fun delete(entry: MealEntry) =
        mealEntryDao.delete(entry.toEntity())

    override suspend fun getByDate(date: String): List<MealEntry> =
        mealEntryDao.entriesForDate(date).map { it.toDomain() }

    override suspend fun getByDateAndMeal(date: String, meal: String): List<MealEntry> =
        mealEntryDao.entriesForDateAndMeal(date, meal).map { it.toDomain() }

    override suspend fun getByProduct(productId: Long): List<MealEntry> =
        mealEntryDao.entriesForProduct(productId).map { it.toDomain() }

    override suspend fun getSince(sinceMillis: Long): List<MealEntry> =
        mealEntryDao.entriesSince(sinceMillis).map { it.toDomain() }

    override suspend fun getLatestDateWithMealBefore(date: String, meal: String): String? =
        mealEntryDao.latestDateWithMealBefore(date, meal)

    override suspend fun countByMeal(meal: String): Int =
        mealEntryDao.countForMeal(meal)

    override suspend fun updateLoggedAtForMeal(date: String, meal: String, loggedAt: Long) {
        ensureDailyLogExists(date)
        mealEntryDao.updateLoggedAtForMeal(date, meal, loggedAt)
    }
}
