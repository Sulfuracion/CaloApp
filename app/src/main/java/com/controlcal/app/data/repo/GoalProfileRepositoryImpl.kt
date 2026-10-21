package com.controlcal.app.data.repo

import com.controlcal.app.data.db.dao.GoalProfileDao
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.repo.GoalProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalProfileRepositoryImpl @Inject constructor(
    private val goalProfileDao: GoalProfileDao,
) : GoalProfileRepository {

    override suspend fun insert(profile: GoalProfile): Long =
        goalProfileDao.insert(profile.toEntity())

    override suspend fun getProfileForDayType(dayType: DayType, date: String): GoalProfile? =
        goalProfileDao.profileForDayType(dayType, date)?.toDomain()

    override suspend fun getProfilesForDayTypeUpTo(dayType: DayType, date: String): List<GoalProfile> =
        goalProfileDao.profilesForDayTypeUpTo(dayType, date).map { it.toDomain() }

    override suspend fun getAll(): List<GoalProfile> =
        goalProfileDao.getAll().map { it.toDomain() }

    override suspend fun deleteAll() =
        goalProfileDao.deleteAll()
}
