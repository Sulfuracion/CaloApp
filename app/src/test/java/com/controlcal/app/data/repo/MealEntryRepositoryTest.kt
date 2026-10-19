package com.controlcal.app.data.repo

import com.controlcal.app.data.db.DaoTestBase
import com.controlcal.app.data.db.entity.MealEntryEntity
import com.controlcal.app.data.db.entity.ProductEntity
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.ProductSource
import com.controlcal.core.nutrition.MealEntrySaveResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MealEntryRepositoryTest : DaoTestBase() {

    private lateinit var repository: MealEntryRepositoryImpl
    private var testProductId: Long = 0L

    @Before
    fun setup() = runBlocking {
        repository = MealEntryRepositoryImpl(db, db.mealEntryDao(), db.dailyLogDao())

        testProductId = db.productDao().insert(
            ProductEntity(
                name = "Avena",
                brand = "Marca",
                kcal100g = 370.0,
                protein100g = 13.0,
                carbs100g = 60.0,
                fat100g = 7.0,
                source = ProductSource.MANUAL,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
    }

    @Test
    fun save_simpleSum_mergesIntoSingleEntryWithSummedValues() = runBlocking {
        val entry1 = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "BREAKFAST",
            productId = testProductId,
            productName = "Avena",
            grams = 50.0,
            kcal = 185.0,
            protein = 6.5,
            carbs = 30.0,
            fat = 3.5,
            loggedAt = 1000L,
        )

        val result1 = repository.save(entry1)
        assertTrue(result1 is MealEntrySaveResult.Created)
        val createdId = (result1 as MealEntrySaveResult.Created).entry.id

        val entry2 = entry1.copy(
            id = 0L,
            grams = 50.0,
            kcal = 185.0,
            protein = 6.5,
            carbs = 30.0,
            fat = 3.5,
            loggedAt = 2000L,
        )

        val result2 = repository.save(entry2)
        assertTrue(result2 is MealEntrySaveResult.Merged)
        val merged = (result2 as MealEntrySaveResult.Merged).mergedEntry
        val previous = result2.previousEntry

        assertEquals(createdId, merged.id)
        assertEquals(50.0, previous.grams, 0.001)
        assertEquals(100.0, merged.grams, 0.001)
        assertEquals(370.0, merged.kcal, 0.001)
        assertEquals(13.0, merged.protein, 0.001)
        assertEquals(60.0, merged.carbs, 0.001)
        assertEquals(7.0, merged.fat, 0.001)
        assertEquals(1000L, merged.loggedAt)

        val inDb = repository.getByDateAndMeal("2026-10-17", "BREAKFAST")
        assertEquals(1, inDb.size)
        assertEquals(100.0, inDb[0].grams, 0.001)
    }

    @Test
    fun save_tareEntry_doesNotMergeIntoPositiveEntry() = runBlocking {
        val positive = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "BREAKFAST",
            productId = testProductId,
            productName = "Avena",
            grams = 100.0,
            kcal = 370.0,
            protein = 13.0,
            carbs = 60.0,
            fat = 7.0,
            loggedAt = 1000L,
        )
        repository.save(positive)

        val tare = positive.copy(
            id = 0L,
            grams = -20.0,
            kcal = -74.0,
            protein = -2.6,
            carbs = -12.0,
            fat = -1.4,
            loggedAt = 1500L,
        )
        val tareResult = repository.save(tare)
        assertTrue(tareResult is MealEntrySaveResult.Created)

        val inDb = repository.getByDateAndMeal("2026-10-17", "BREAKFAST")
        assertEquals(2, inDb.size)
        assertTrue(inDb.any { it.grams == 100.0 })
        assertTrue(inDb.any { it.grams == -20.0 })
    }

    @Test
    fun save_quickCalories_doesNotMerge() = runBlocking {
        val quickCal1 = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "LUNCH",
            productId = null,
            productName = "Calorías rápidas",
            grams = 0.0,
            kcal = 200.0,
            protein = 5.0,
            carbs = 20.0,
            fat = 10.0,
            loggedAt = 1000L,
        )
        val result1 = repository.save(quickCal1)
        assertTrue(result1 is MealEntrySaveResult.Created)

        val quickCal2 = quickCal1.copy(id = 0L, kcal = 300.0, loggedAt = 2000L)
        val result2 = repository.save(quickCal2)
        assertTrue(result2 is MealEntrySaveResult.Created)

        val inDb = repository.getByDateAndMeal("2026-10-17", "LUNCH")
        assertEquals(2, inDb.size)
    }

    @Test
    fun save_differentMeal_doesNotMerge() = runBlocking {
        val breakfast = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "BREAKFAST",
            productId = testProductId,
            productName = "Avena",
            grams = 50.0,
            kcal = 185.0,
            protein = 6.5,
            carbs = 30.0,
            fat = 3.5,
            loggedAt = 1000L,
        )
        repository.save(breakfast)

        val lunch = breakfast.copy(id = 0L, meal = "LUNCH", loggedAt = 2000L)
        val lunchResult = repository.save(lunch)
        assertTrue(lunchResult is MealEntrySaveResult.Created)

        assertEquals(1, repository.getByDateAndMeal("2026-10-17", "BREAKFAST").size)
        assertEquals(1, repository.getByDateAndMeal("2026-10-17", "LUNCH").size)
    }

    @Test
    fun save_differentDate_doesNotMerge() = runBlocking {
        val day1 = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "BREAKFAST",
            productId = testProductId,
            productName = "Avena",
            grams = 50.0,
            kcal = 185.0,
            protein = 6.5,
            carbs = 30.0,
            fat = 3.5,
            loggedAt = 1000L,
        )
        repository.save(day1)

        val day2 = day1.copy(id = 0L, date = "2026-10-18", loggedAt = 2000L)
        val day2Result = repository.save(day2)
        assertTrue(day2Result is MealEntrySaveResult.Created)

        assertEquals(1, repository.getByDate("2026-10-17").size)
        assertEquals(1, repository.getByDate("2026-10-18").size)
    }

    @Test
    fun save_multipleLegacyDuplicates_mergesIntoEarliestAndPreservesOthers() = runBlocking {
        db.dailyLogDao().insert(
            com.controlcal.app.data.db.entity.DailyLogEntity(
                date = "2026-10-17",
                dayType = com.controlcal.core.model.DayType.REST
            )
        )
        // Manually insert two legacy duplicates directly into DAO
        val id1 = db.mealEntryDao().insert(
            MealEntryEntity(
                date = "2026-10-17",
                meal = "BREAKFAST",
                productId = testProductId,
                productName = "Avena",
                grams = 40.0,
                kcal = 148.0,
                protein = 5.2,
                carbs = 24.0,
                fat = 2.8,
                loggedAt = 1000L,
            )
        )
        val id2 = db.mealEntryDao().insert(
            MealEntryEntity(
                date = "2026-10-17",
                meal = "BREAKFAST",
                productId = testProductId,
                productName = "Avena",
                grams = 30.0,
                kcal = 111.0,
                protein = 3.9,
                carbs = 18.0,
                fat = 2.1,
                loggedAt = 2000L,
            )
        )

        // New entry to save
        val newEntry = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "BREAKFAST",
            productId = testProductId,
            productName = "Avena",
            grams = 50.0,
            kcal = 185.0,
            protein = 6.5,
            carbs = 30.0,
            fat = 3.5,
            loggedAt = 3000L,
        )

        val result = repository.save(newEntry)
        assertTrue(result is MealEntrySaveResult.Merged)
        val merged = (result as MealEntrySaveResult.Merged).mergedEntry

        // Should merge into the earliest (id1)
        assertEquals(id1, merged.id)
        assertEquals(90.0, merged.grams, 0.001) // 40 + 50 = 90
        assertEquals(1000L, merged.loggedAt)

        // In DB there should still be 2 rows (duplicates not deleted/merged retroactively)
        val inDb = repository.getByDateAndMeal("2026-10-17", "BREAKFAST")
        assertEquals(2, inDb.size)

        val row1 = inDb.first { it.id == id1 }
        val row2 = inDb.first { it.id == id2 }
        assertEquals(90.0, row1.grams, 0.001)
        assertEquals(30.0, row2.grams, 0.001) // Unchanged
    }

    @Test
    fun save_undoRestoresExactPreviousRow() = runBlocking {
        val entry1 = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "BREAKFAST",
            productId = testProductId,
            productName = "Avena",
            grams = 50.0,
            kcal = 185.0,
            protein = 6.5,
            carbs = 30.0,
            fat = 3.5,
            loggedAt = 1000L,
        )
        val res1 = repository.save(entry1) as MealEntrySaveResult.Created

        val entry2 = entry1.copy(id = 0L, grams = 30.0, kcal = 111.0, loggedAt = 2000L)
        val res2 = repository.save(entry2) as MealEntrySaveResult.Merged

        assertEquals(80.0, res2.mergedEntry.grams, 0.001)
        assertEquals(50.0, res2.previousEntry.grams, 0.001)

        // Undo: update with previousEntry
        repository.update(res2.previousEntry)

        val restored = repository.getByDateAndMeal("2026-10-17", "BREAKFAST")
        assertEquals(1, restored.size)
        assertEquals(res1.entry.id, restored[0].id)
        assertEquals(50.0, restored[0].grams, 0.001)
        assertEquals(185.0, restored[0].kcal, 0.001)
        assertEquals(1000L, restored[0].loggedAt)
    }

    @Test
    fun save_productEditedBetweenAdditions_preservesOldSnapshot() = runBlocking {
        // Save initial entry
        val entry1 = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "BREAKFAST",
            productId = testProductId,
            productName = "Avena",
            grams = 100.0,
            kcal = 370.0,
            protein = 13.0,
            carbs = 60.0,
            fat = 7.0,
            loggedAt = 1000L,
        )
        repository.save(entry1)

        // Product in DB is updated with new nutritional profile (e.g. 500 kcal / 100g)
        db.productDao().update(
            ProductEntity(
                id = testProductId,
                name = "Avena (nueva receta)",
                brand = "Marca",
                kcal100g = 500.0,
                protein100g = 20.0,
                carbs100g = 70.0,
                fat100g = 15.0,
                source = ProductSource.MANUAL,
                createdAt = 1000L,
                updatedAt = 2000L,
            )
        )

        // Add 100g calculated from new product definition
        val entry2 = MealEntry(
            id = 0L,
            date = "2026-10-17",
            meal = "BREAKFAST",
            productId = testProductId,
            productName = "Avena (nueva receta)",
            grams = 100.0,
            kcal = 500.0,
            protein = 20.0,
            carbs = 70.0,
            fat = 15.0,
            loggedAt = 2000L,
        )
        val res = repository.save(entry2) as MealEntrySaveResult.Merged

        assertEquals(200.0, res.mergedEntry.grams, 0.001)
        // 370 + 500 = 870 kcal (NOT 1000 kcal)
        assertEquals(870.0, res.mergedEntry.kcal, 0.001)
        assertEquals(33.0, res.mergedEntry.protein, 0.001)
        assertEquals(130.0, res.mergedEntry.carbs, 0.001)
        assertEquals(22.0, res.mergedEntry.fat, 0.001)
    }

    @Test
    fun saveAll_copyDayWithProductAlreadyPresent_mergesTargetEntries() = runBlocking {
        // Today already has 50g of oats in breakfast
        repository.save(
            MealEntry(
                id = 0L,
                date = "2026-10-17",
                meal = "BREAKFAST",
                productId = testProductId,
                productName = "Avena",
                grams = 50.0,
                kcal = 185.0,
                protein = 6.5,
                carbs = 30.0,
                fat = 3.5,
                loggedAt = 1000L,
            )
        )

        // Yesterday had 100g of oats in breakfast
        val clonedFromYesterday = listOf(
            MealEntry(
                id = 0L,
                date = "2026-10-17",
                meal = "BREAKFAST",
                productId = testProductId,
                productName = "Avena",
                grams = 100.0,
                kcal = 370.0,
                protein = 13.0,
                carbs = 60.0,
                fat = 7.0,
                loggedAt = 2000L,
            )
        )

        val results = repository.saveAll(clonedFromYesterday)
        assertEquals(1, results.size)
        assertTrue(results[0] is MealEntrySaveResult.Merged)

        val inDb = repository.getByDateAndMeal("2026-10-17", "BREAKFAST")
        assertEquals(1, inDb.size)
        assertEquals(150.0, inDb[0].grams, 0.001)
        assertEquals(555.0, inDb[0].kcal, 0.001)
    }
}
