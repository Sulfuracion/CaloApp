package com.controlcal.core.repo

import com.controlcal.core.model.MealEntry

interface MealEntryRepository {
    suspend fun insert(entry: MealEntry): Long
    suspend fun insertAll(entries: List<MealEntry>): List<Long>
    suspend fun update(entry: MealEntry)
    suspend fun delete(entry: MealEntry)
    suspend fun getByDate(date: String): List<MealEntry>
    suspend fun getByDateAndMeal(date: String, meal: String): List<MealEntry>
    suspend fun getByProduct(productId: Long): List<MealEntry>
    suspend fun getSince(sinceMillis: Long): List<MealEntry>
    suspend fun getLatestDateWithMealBefore(date: String, meal: String): String?
    suspend fun countByMeal(meal: String): Int
    suspend fun updateLoggedAtForMeal(date: String, meal: String, loggedAt: Long) {
        val entries = getByDateAndMeal(date, meal)
        for (entry in entries) {
            update(entry.copy(loggedAt = loggedAt))
        }
    }
}
