package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.controlcal.app.data.db.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    /** One measurement per day: a new measurement replaces the old one. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries WHERE date = :date")
    suspend fun getByDate(date: String): WeightEntryEntity?

    @Query("SELECT * FROM weight_entries WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun entriesBetween(from: String, to: String): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries ORDER BY date ASC")
    suspend fun getAllAsc(): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries ORDER BY date DESC")
    fun observeAllDesc(): Flow<List<WeightEntryEntity>>

    @Query("DELETE FROM weight_entries WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM weight_entries")
    suspend fun deleteAll()

    @Transaction
    suspend fun moveEntry(oldDate: String, newEntry: WeightEntryEntity) {
        if (oldDate != newEntry.date) {
            deleteByDate(oldDate)
        }
        upsert(newEntry)
    }
}

