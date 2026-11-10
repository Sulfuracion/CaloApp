package com.controlcal.app.data.db

import android.database.sqlite.SQLiteConstraintException
import com.controlcal.app.data.db.entity.SavedMealEntity
import com.controlcal.app.data.db.entity.SavedMealItemEntity
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SavedMealDaoTest : DaoTestBase() {

    @Test
    fun insertAndRetrieveMealWithItems() = runTest {
        val prodId = db.productDao().insert(TestData.product(name = "Avena"))
        val dao = db.savedMealDao()

        val mealId = dao.insertMeal(
            SavedMealEntity(
                name = "Desayuno fit",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        dao.insertItems(
            listOf(
                SavedMealItemEntity(
                    savedMealId = mealId,
                    position = 0,
                    productId = prodId,
                    productName = "Avena",
                    grams = 80.0,
                ),
                SavedMealItemEntity(
                    savedMealId = mealId,
                    position = 1,
                    productId = null,
                    productName = "Proteína polvo",
                    grams = 0.0,
                    kcal = 120.0,
                    protein = 24.0,
                    carbs = 2.0,
                    fat = 1.0,
                ),
            )
        )

        val retrieved = dao.getWithItemsById(mealId)
        assertNotNull(retrieved)
        assertEquals("Desayuno fit", retrieved!!.meal.name)
        assertEquals(2, retrieved.items.size)
        assertEquals(0, retrieved.items[0].position)
        assertEquals("Avena", retrieved.items[0].productName)
        assertEquals(80.0, retrieved.items[0].grams, 0.001)
        assertEquals("Proteína polvo", retrieved.items[1].productName)
        assertEquals(120.0, retrieved.items[1].kcal!!, 0.001)
    }

    @Test
    fun uniqueNameCaseInsensitiveThrowsOnDuplicate() = runTest {
        val dao = db.savedMealDao()

        dao.insertMeal(
            SavedMealEntity(
                name = "Tortilla de patatas",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        assertFailsWith<SQLiteConstraintException> {
            dao.insertMeal(
                SavedMealEntity(
                    name = "TORTILLA DE PATATAS",
                    createdAt = 2000L,
                    updatedAt = 2000L,
                )
            )
        }
    }

    @Test
    fun deleteMealCascadesToItems() = runTest {
        val dao = db.savedMealDao()

        val mealId = dao.insertMeal(
            SavedMealEntity(
                name = "Merienda",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        dao.insertItems(
            listOf(
                SavedMealItemEntity(
                    savedMealId = mealId,
                    position = 0,
                    productId = null,
                    productName = "Fruta",
                    grams = 0.0,
                    kcal = 60.0,
                )
            )
        )

        dao.deleteMealById(mealId)

        assertNull(dao.getWithItemsById(mealId))
        val count = dao.countItemsForMeal(mealId)
        assertEquals(0, count)
    }

    @Test
    fun recordUsageUpdatesTimestampAndIncrementsCount() = runTest {
        val dao = db.savedMealDao()

        val mealId = dao.insertMeal(
            SavedMealEntity(
                name = "Cena ligera",
                createdAt = 1000L,
                updatedAt = 1000L,
                useCount = 0,
                lastUsedAt = null,
            )
        )

        dao.recordUsage(mealId, 5000L)

        val updated = dao.getWithItemsById(mealId)
        assertNotNull(updated)
        assertEquals(1, updated!!.meal.useCount)
        assertEquals(5000L, updated.meal.lastUsedAt)

        dao.recordUsage(mealId, 7000L)
        val updated2 = dao.getWithItemsById(mealId)
        assertEquals(2, updated2!!.meal.useCount)
        assertEquals(7000L, updated2.meal.lastUsedAt)
    }
}
