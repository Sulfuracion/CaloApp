package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.controlcal.app.data.db.entity.GoalProfileEntity
import com.controlcal.core.model.DayType

@Dao
interface GoalProfileDao {

    @Insert
    suspend fun insert(profile: GoalProfileEntity): Long

    /**
     * Goal in force for [date] and [dayType]: the latest profile whose
     * valid_from is not after [date] (section 5.1 of docs/PLAN.md).
     */
    @Query(
        "SELECT * FROM goal_profiles " +
            "WHERE day_type = :dayType AND valid_from <= :date " +
            "ORDER BY valid_from DESC, id DESC LIMIT 1",
    )
    suspend fun profileForDayType(dayType: DayType, date: String): GoalProfileEntity?

    @Query(
        "SELECT * FROM goal_profiles " +
            "WHERE day_type = :dayType AND valid_from <= :date " +
            "ORDER BY valid_from DESC, id DESC",
    )
    suspend fun profilesForDayTypeUpTo(dayType: DayType, date: String): List<GoalProfileEntity>

    @Query("SELECT * FROM goal_profiles ORDER BY valid_from ASC, id ASC")
    suspend fun getAll(): List<GoalProfileEntity>

    @Query("DELETE FROM goal_profiles")
    suspend fun deleteAll()
}
