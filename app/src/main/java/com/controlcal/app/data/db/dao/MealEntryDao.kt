package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.controlcal.app.data.db.entity.MealEntryEntity
import com.controlcal.core.model.Meal

@Dao
interface MealEntryDao {

    @Insert
    suspend fun insert(entry: MealEntryEntity): Long

    @Update
    suspend fun update(entry: MealEntryEntity)

    @Delete
    suspend fun delete(entry: MealEntryEntity)

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY logged_at")
    suspend fun entriesForDate(date: String): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries WHERE date = :date AND meal = :meal ORDER BY logged_at")
    suspend fun entriesForDateAndMeal(date: String, meal: Meal): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries WHERE product_id = :productId ORDER BY logged_at")
    suspend fun entriesForProduct(productId: Long): List<MealEntryEntity>
}
