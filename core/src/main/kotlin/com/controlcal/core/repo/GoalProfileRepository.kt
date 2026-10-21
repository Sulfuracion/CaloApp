package com.controlcal.core.repo

import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalProfile

interface GoalProfileRepository {
    suspend fun insert(profile: GoalProfile): Long
    suspend fun getProfileForDayType(dayType: DayType, date: String): GoalProfile?
    suspend fun getProfilesForDayTypeUpTo(dayType: DayType, date: String): List<GoalProfile>
    suspend fun getAll(): List<GoalProfile> = emptyList()
    suspend fun deleteAll() {}
}
