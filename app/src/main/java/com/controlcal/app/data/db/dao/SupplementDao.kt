package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.controlcal.app.data.db.entity.SupplementEntity
import com.controlcal.app.data.db.entity.SupplementIntakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplementDao {

    @Query("SELECT * FROM supplements ORDER BY name COLLATE NOCASE ASC")
    fun getAllFlow(): Flow<List<SupplementEntity>>

    @Query("SELECT * FROM supplements ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<SupplementEntity>

    @Query("SELECT * FROM supplements WHERE active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun getActiveFlow(): Flow<List<SupplementEntity>>

    @Query("SELECT * FROM supplements WHERE active = 1 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getActive(): List<SupplementEntity>

    @Query("SELECT * FROM supplements WHERE id = :id")
    suspend fun getById(id: Long): SupplementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(supplement: SupplementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(supplements: List<SupplementEntity>)

    @Update
    suspend fun update(supplement: SupplementEntity)

    @Query("UPDATE supplements SET active = :active, updated_at = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM supplements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM supplements")
    suspend fun deleteAll()

    // Intakes
    @Query("SELECT * FROM supplement_intakes WHERE scheduled_date = :date")
    fun getIntakesForDateFlow(date: String): Flow<List<SupplementIntakeEntity>>

    @Query("SELECT * FROM supplement_intakes WHERE scheduled_date = :date")
    suspend fun getIntakesForDate(date: String): List<SupplementIntakeEntity>

    @Query("SELECT * FROM supplement_intakes WHERE scheduled_date BETWEEN :startDate AND :endDate")
    suspend fun getIntakesBetween(startDate: String, endDate: String): List<SupplementIntakeEntity>

    @Query("SELECT * FROM supplement_intakes WHERE supplement_id = :supplementId AND scheduled_date = :date AND scheduled_time = :time LIMIT 1")
    suspend fun getIntake(supplementId: Long, date: String, time: String): SupplementIntakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntake(intake: SupplementIntakeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIntakeIgnore(intake: SupplementIntakeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllIntakes(intakes: List<SupplementIntakeEntity>)

    @Query("DELETE FROM supplement_intakes WHERE supplement_id = :supplementId AND scheduled_date = :date AND scheduled_time = :time")
    suspend fun deleteIntake(supplementId: Long, date: String, time: String)

    @Query("DELETE FROM supplement_intakes")
    suspend fun deleteAllIntakes()

    @Query("SELECT * FROM supplement_intakes")
    suspend fun getAllIntakes(): List<SupplementIntakeEntity>
}
