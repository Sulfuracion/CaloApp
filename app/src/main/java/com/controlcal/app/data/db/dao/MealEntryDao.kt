package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.controlcal.app.data.db.entity.MealEntryEntity

@Dao
interface MealEntryDao {

    @Insert
    suspend fun insert(entry: MealEntryEntity): Long

    @Update
    suspend fun update(entry: MealEntryEntity)

    @Delete
    suspend fun delete(entry: MealEntryEntity)

    @Insert
    suspend fun insertAll(entries: List<MealEntryEntity>): List<Long>

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY logged_at")
    suspend fun entriesForDate(date: String): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries WHERE date = :date AND meal = :meal ORDER BY logged_at")
    suspend fun entriesForDateAndMeal(date: String, meal: String): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries WHERE product_id = :productId ORDER BY logged_at")
    suspend fun entriesForProduct(productId: Long): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries WHERE logged_at >= :sinceMillis ORDER BY logged_at DESC")
    suspend fun entriesSince(sinceMillis: Long): List<MealEntryEntity>

    @Query("SELECT date FROM meal_entries WHERE date < :date AND meal = :meal ORDER BY date DESC LIMIT 1")
    suspend fun latestDateWithMealBefore(date: String, meal: String): String?

    @Query("SELECT COUNT(*) FROM meal_entries WHERE meal = :meal")
    suspend fun countForMeal(meal: String): Int

    @Query("UPDATE meal_entries SET logged_at = :loggedAt WHERE date = :date AND meal = :meal")
    suspend fun updateLoggedAtForMeal(date: String, meal: String, loggedAt: Long): Int
}
