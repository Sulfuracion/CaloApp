package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.controlcal.app.data.db.entity.SavedMealEntity
import com.controlcal.app.data.db.entity.SavedMealItemEntity
import com.controlcal.app.data.db.entity.SavedMealWithItemsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMealDao {

    @Transaction
    @Query("SELECT * FROM saved_meals ORDER BY last_used_at DESC, use_count DESC, created_at DESC")
    fun observeAllWithItems(): Flow<List<SavedMealWithItemsEntity>>

    @Transaction
    @Query("SELECT * FROM saved_meals ORDER BY last_used_at DESC, use_count DESC, created_at DESC")
    suspend fun getAllWithItems(): List<SavedMealWithItemsEntity>

    @Transaction
    @Query("SELECT * FROM saved_meals WHERE id = :id")
    suspend fun getWithItemsById(id: Long): SavedMealWithItemsEntity?

    @Query("SELECT * FROM saved_meals WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): SavedMealEntity?

    @Query("SELECT COUNT(*) FROM saved_meals WHERE name = :name COLLATE NOCASE AND (:excludeId IS NULL OR id != :excludeId)")
    suspend fun countByNameExcluding(name: String, excludeId: Long? = null): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeal(meal: SavedMealEntity): Long

    @Update
    suspend fun updateMeal(meal: SavedMealEntity)

    @Delete
    suspend fun deleteMeal(meal: SavedMealEntity)

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun deleteMealById(id: Long)

    @Insert
    suspend fun insertItems(items: List<SavedMealItemEntity>)

    @Update
    suspend fun updateItem(item: SavedMealItemEntity)

    @Delete
    suspend fun deleteItem(item: SavedMealItemEntity)

    @Query("DELETE FROM saved_meal_items WHERE saved_meal_id = :savedMealId")
    suspend fun deleteItemsForMeal(savedMealId: Long)

    @Query("UPDATE saved_meals SET last_used_at = :usedAt, use_count = use_count + 1 WHERE id = :id")
    suspend fun recordUsage(id: Long, usedAt: Long)

    @Transaction
    @Query("SELECT * FROM saved_meals WHERE name LIKE '%' || :query || '%' COLLATE NOCASE")
    suspend fun searchByName(query: String): List<SavedMealWithItemsEntity>

    @Query("DELETE FROM saved_meal_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM saved_meals")
    suspend fun deleteAllMeals()

    @Query("SELECT COUNT(*) FROM saved_meal_items WHERE saved_meal_id = :savedMealId")
    suspend fun countItemsForMeal(savedMealId: Long): Int

    @Query("SELECT COUNT(*) FROM saved_meal_items WHERE product_id = :productId")
    suspend fun countItemsForProduct(productId: Long): Int
}
