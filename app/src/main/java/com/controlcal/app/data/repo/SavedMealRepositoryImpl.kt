package com.controlcal.app.data.repo

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.controlcal.app.data.db.ControlCalDatabase
import com.controlcal.app.data.db.dao.SavedMealDao
import com.controlcal.app.data.db.entity.SavedMealEntity
import com.controlcal.app.data.db.entity.SavedMealItemEntity
import com.controlcal.core.model.SavedMeal
import com.controlcal.core.repo.SavedMealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedMealRepositoryImpl @Inject constructor(
    private val database: ControlCalDatabase,
    private val savedMealDao: SavedMealDao,
) : SavedMealRepository {

    fun observeAll(): Flow<List<SavedMeal>> =
        savedMealDao.observeAllWithItems().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<SavedMeal> =
        savedMealDao.getAllWithItems().map { it.toDomain() }

    override suspend fun getById(id: Long): SavedMeal? =
        savedMealDao.getWithItemsById(id)?.toDomain()

    override suspend fun getByName(name: String): SavedMeal? =
        savedMealDao.getByName(name.trim())?.let { savedMealDao.getWithItemsById(it.id)?.toDomain() }

    override suspend fun isNameTaken(name: String, excludeId: Long?): Boolean =
        savedMealDao.countByNameExcluding(name.trim(), excludeId) > 0

    override suspend fun saveMeal(meal: SavedMeal): Long {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val entity = SavedMealEntity.fromDomain(meal)
                val mealId = savedMealDao.insertMeal(entity)
                val itemEntities = meal.items.mapIndexed { index, item ->
                    SavedMealItemEntity.fromDomain(
                        domain = item.copy(position = index),
                        savedMealId = mealId,
                    )
                }
                if (itemEntities.isNotEmpty()) {
                    savedMealDao.insertItems(itemEntities)
                }
                mealId
            }
        }
    }

    override suspend fun updateMeal(meal: SavedMeal) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                savedMealDao.updateMeal(SavedMealEntity.fromDomain(meal))
                savedMealDao.deleteItemsForMeal(meal.id)
                val itemEntities = meal.items.mapIndexed { index, item ->
                    SavedMealItemEntity.fromDomain(
                        domain = item.copy(position = index),
                        savedMealId = meal.id,
                    )
                }
                if (itemEntities.isNotEmpty()) {
                    savedMealDao.insertItems(itemEntities)
                }
            }
        }
    }

    override suspend fun deleteMeal(id: Long) {
        savedMealDao.deleteMealById(id)
    }

    override suspend fun recordMealUsage(id: Long, usedAt: Long) {
        savedMealDao.recordUsage(id, usedAt)
    }

    override suspend fun searchByName(query: String): List<SavedMeal> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getAll()
        return savedMealDao.searchByName(trimmed).map { it.toDomain() }
    }

    override suspend fun deleteAll() {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                savedMealDao.deleteAllItems()
                savedMealDao.deleteAllMeals()
            }
        }
    }
}
