package com.controlcal.app.data.repo

import com.controlcal.app.data.db.dao.DailyLogDao
import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.UserSettingsRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyLogRepositoryImpl @Inject constructor(
    private val dailyLogDao: DailyLogDao,
    private val userSettingsRepository: UserSettingsRepository = object : UserSettingsRepository {
        override suspend fun getWeeklyPattern(): WeeklyDayPattern = WeeklyDayPattern()
        override suspend fun setWeeklyPattern(pattern: WeeklyDayPattern) {}
        override suspend fun getMinKcalLimit(): Double = 1200.0
        override suspend fun setMinKcalLimit(minKcal: Double) {}
        override suspend fun getLastTdeeProposalReviewedDate(): String? = null
        override suspend fun setLastTdeeProposalReviewedDate(date: String) {}
    },
) : DailyLogRepository {

    private suspend fun resolveDefaultDayType(date: String): DayType {
        return try {
            val localDate = LocalDate.parse(date)
            userSettingsRepository.getWeeklyPattern().dayTypeFor(localDate.dayOfWeek)
        } catch (_: Exception) {
            DayType.REST
        }
    }

    override suspend fun getByDate(date: String): DailyLog? =
        dailyLogDao.getByDate(date)?.toDomain()

    override suspend fun getAll(): List<DailyLog> =
        dailyLogDao.getAll().map { it.toDomain() }

    override suspend fun insert(log: DailyLog) =
        dailyLogDao.insert(log.toEntity())

    override suspend fun setDayType(date: String, dayType: DayType) {
        val existing = dailyLogDao.getByDate(date)
        if (existing != null) {
            dailyLogDao.setDayType(date, dayType)
        } else {
            dailyLogDao.insert(DailyLogEntity(date = date, dayType = dayType))
        }
    }

    override suspend fun setWaterMl(date: String, waterMl: Int) {
        val coercedWaterMl = waterMl.coerceAtLeast(0)
        val existing = dailyLogDao.getByDate(date)
        if (existing != null) {
            dailyLogDao.setWaterMl(date, coercedWaterMl)
        } else {
            val dayType = resolveDefaultDayType(date)
            dailyLogDao.insert(DailyLogEntity(date = date, dayType = dayType, waterMl = coercedWaterMl))
        }
    }

    override suspend fun setNotes(date: String, notes: String?) {
        val existing = dailyLogDao.getByDate(date)
        if (existing != null) {
            dailyLogDao.setNotes(date, notes)
        } else {
            val dayType = resolveDefaultDayType(date)
            dailyLogDao.insert(DailyLogEntity(date = date, dayType = dayType, notes = notes))
        }
    }

    override suspend fun deleteAll() =
        dailyLogDao.deleteAll()
}
