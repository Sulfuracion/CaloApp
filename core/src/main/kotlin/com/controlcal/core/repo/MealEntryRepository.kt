package com.controlcal.core.repo

import com.controlcal.core.model.MealEntry
import com.controlcal.core.nutrition.MealEntrySaveResult

interface MealEntryRepository {
    suspend fun insert(entry: MealEntry): Long
    suspend fun insertAll(entries: List<MealEntry>): List<Long> = entries.map { insert(it) }

    suspend fun save(entry: MealEntry): MealEntrySaveResult {
        val existing = getByDateAndMeal(entry.date, entry.meal)
        val candidate = com.controlcal.core.nutrition.MealEntryMerger.findMergeCandidate(existing, entry)
        return if (candidate != null) {
            val merged = com.controlcal.core.nutrition.MealEntryMerger.merge(candidate, entry)
            update(merged)
            MealEntrySaveResult.Merged(merged, candidate)
        } else {
            val id = insert(entry)
            MealEntrySaveResult.Created(entry.copy(id = id))
        }
    }

    suspend fun saveAll(entries: List<MealEntry>): List<MealEntrySaveResult> {
        return entries.map { save(it) }
    }

    suspend fun update(entry: MealEntry)
    suspend fun delete(entry: MealEntry)
    suspend fun getByDate(date: String): List<MealEntry>
    suspend fun getByDateAndMeal(date: String, meal: String): List<MealEntry>
    suspend fun getByProduct(productId: Long): List<MealEntry>
    suspend fun getSince(sinceMillis: Long): List<MealEntry>
    suspend fun getAll(): List<MealEntry> = getSince(0L)
    suspend fun deleteAll() {}
    suspend fun getLatestDateWithMealBefore(date: String, meal: String): String?
    suspend fun countByMeal(meal: String): Int
    suspend fun updateLoggedAtForMeal(date: String, meal: String, loggedAt: Long) {
        val entries = getByDateAndMeal(date, meal)
        for (entry in entries) {
            update(entry.copy(loggedAt = loggedAt))
        }
    }
}
