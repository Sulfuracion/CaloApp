package com.controlcal.app.data.repo

import com.controlcal.app.data.db.dao.DailyLogDao
import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType
import com.controlcal.core.repo.DailyLogRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyLogRepositoryImpl @Inject constructor(
    private val dailyLogDao: DailyLogDao,
) : DailyLogRepository {

    override suspend fun getByDate(date: String): DailyLog? =
        dailyLogDao.getByDate(date)?.toDomain()

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

    override suspend fun setWaterMl(date: String, waterMl: Int) =
        dailyLogDao.setWaterMl(date, waterMl)

    override suspend fun setNotes(date: String, notes: String?) =
        dailyLogDao.setNotes(date, notes)
}
