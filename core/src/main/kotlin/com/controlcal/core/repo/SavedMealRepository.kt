package com.controlcal.core.repo

import com.controlcal.core.model.SavedMeal

/**
 * Pure repository interface for managing saved meals and their items.
 */
interface SavedMealRepository {
    suspend fun getAll(): List<SavedMeal>
    suspend fun getById(id: Long): SavedMeal?
    suspend fun getByName(name: String): SavedMeal?
    suspend fun isNameTaken(name: String, excludeId: Long? = null): Boolean
    suspend fun saveMeal(meal: SavedMeal): Long
    suspend fun updateMeal(meal: SavedMeal)
    suspend fun deleteMeal(id: Long)
    suspend fun recordMealUsage(id: Long, usedAt: Long = System.currentTimeMillis())
    suspend fun searchByName(query: String): List<SavedMeal>
    suspend fun deleteAll()
}
