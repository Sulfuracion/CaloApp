package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.controlcal.app.data.db.entity.WeightEntryEntity

@Dao
interface WeightEntryDao {

    /** One measurement per day: a new measurement replaces the old one. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries WHERE date = :date")
    suspend fun getByDate(date: String): WeightEntryEntity?

    @Query("SELECT * FROM weight_entries WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun entriesBetween(from: String, to: String): List<WeightEntryEntity>
}
