package com.controlcal.core.repo

import com.controlcal.core.model.Meal
import com.controlcal.core.model.MealEntry

interface MealEntryRepository {
    suspend fun insert(entry: MealEntry): Long
    suspend fun update(entry: MealEntry)
    suspend fun delete(entry: MealEntry)
    suspend fun getByDate(date: String): List<MealEntry>
    suspend fun getByDateAndMeal(date: String, meal: Meal): List<MealEntry>
    suspend fun getByProduct(productId: Long): List<MealEntry>
}
