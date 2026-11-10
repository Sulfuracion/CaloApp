package com.controlcal.app.test

import com.controlcal.core.model.SavedMeal
import com.controlcal.core.repo.SavedMealRepository
import com.controlcal.core.search.TextNormalizer

class FakeSavedMealRepository : SavedMealRepository {
    val savedMeals = mutableListOf<SavedMeal>()
    private var nextId = 1L

    override suspend fun getAll(): List<SavedMeal> = savedMeals.toList()

    override suspend fun getById(id: Long): SavedMeal? = savedMeals.find { it.id == id }

    override suspend fun getByName(name: String): SavedMeal? =
        savedMeals.find { it.name.equals(name.trim(), ignoreCase = true) }

    override suspend fun isNameTaken(name: String, excludeId: Long?): Boolean =
        savedMeals.any { it.name.equals(name.trim(), ignoreCase = true) && it.id != excludeId }

    override suspend fun saveMeal(meal: SavedMeal): Long {
        val id = if (meal.id == 0L) nextId++ else meal.id
        savedMeals.removeAll { it.id == id }
        savedMeals.add(meal.copy(id = id))
        return id
    }

    override suspend fun updateMeal(meal: SavedMeal) {
        val idx = savedMeals.indexOfFirst { it.id == meal.id }
        if (idx >= 0) {
            savedMeals[idx] = meal
        }
    }

    override suspend fun deleteMeal(id: Long) {
        savedMeals.removeAll { it.id == id }
    }

    override suspend fun recordMealUsage(id: Long, usedAt: Long) {
        val idx = savedMeals.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val m = savedMeals[idx]
            savedMeals[idx] = m.copy(
                lastUsedAt = usedAt,
                useCount = m.useCount + 1,
            )
        }
    }

    override suspend fun searchByName(query: String): List<SavedMeal> {
        val normalized = TextNormalizer.normalize(query)
        return savedMeals.filter {
            TextNormalizer.normalize(it.name).contains(normalized, ignoreCase = true)
        }
    }

    override suspend fun deleteAll() {
        savedMeals.clear()
    }
}
