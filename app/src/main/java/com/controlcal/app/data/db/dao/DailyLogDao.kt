package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.core.model.DayType

@Dao
interface DailyLogDao {

    @Insert
    suspend fun insert(log: DailyLogEntity)

    @Query("SELECT * FROM daily_logs WHERE date = :date")
    suspend fun getByDate(date: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs ORDER BY date ASC")
    suspend fun getAll(): List<DailyLogEntity>

    @Query("UPDATE daily_logs SET day_type = :dayType WHERE date = :date")
    suspend fun setDayType(date: String, dayType: DayType)

    @Query("UPDATE daily_logs SET water_ml = :waterMl WHERE date = :date")
    suspend fun setWaterMl(date: String, waterMl: Int)

    @Query("UPDATE daily_logs SET notes = :notes WHERE date = :date")
    suspend fun setNotes(date: String, notes: String?)

    @Query("DELETE FROM daily_logs")
    suspend fun deleteAll()
}
