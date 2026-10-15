package com.controlcal.app.data.db

import android.database.sqlite.SQLiteConstraintException
import com.controlcal.core.model.DefaultMealIds
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MealEntryDaoTest : DaoTestBase() {

    @Test
    fun entriesForDateReturnsOnlyThatDayOrderedByLoggedAt() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog(date = TestData.DATE_A))
        db.dailyLogDao().insert(TestData.dailyLog(date = TestData.DATE_B))
        val productId = db.productDao().insert(TestData.product(name = "Pan integral"))
        val dao = db.mealEntryDao()

        // DATE_A: inserted out of chronological order.
        dao.insert(
            TestData.mealEntry(date = TestData.DATE_A, productId = productId, productName = "Pan integral", loggedAt = 3_000L),
        )
        dao.insert(
            TestData.mealEntry(date = TestData.DATE_A, productId = productId, productName = "Pan integral", loggedAt = 1_000L),
        )
        // DATE_B: must not leak into DATE_A reads.
        dao.insert(
            TestData.mealEntry(date = TestData.DATE_B, productId = productId, productName = "Pan integral", loggedAt = 2_000L),
        )

        val dayA = dao.entriesForDate(TestData.DATE_A)
        assertEquals(2, dayA.size)
        assertEquals(listOf(1_000L, 3_000L), dayA.map { it.loggedAt })
        assertEquals(listOf(TestData.DATE_A, TestData.DATE_A), dayA.map { it.date })

        val dayB = dao.entriesForDate(TestData.DATE_B)
        assertEquals(1, dayB.size)
    }

    @Test
    fun entriesForDateAndMealFiltersByMeal() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog())
        val productId = db.productDao().insert(TestData.product(name = "Huevo"))
        val dao = db.mealEntryDao()

        dao.insert(TestData.mealEntry(meal = DefaultMealIds.BREAKFAST, productId = productId, productName = "Huevo"))
        dao.insert(TestData.mealEntry(meal = DefaultMealIds.DINNER, productId = productId, productName = "Huevo", loggedAt = 2_000L))

        assertEquals(1, dao.entriesForDateAndMeal(TestData.DATE_A, DefaultMealIds.BREAKFAST).size)
        assertEquals(1, dao.entriesForDateAndMeal(TestData.DATE_A, DefaultMealIds.DINNER).size)
        assertEquals(0, dao.entriesForDateAndMeal(TestData.DATE_A, DefaultMealIds.SNACK).size)
    }

    @Test
    fun negativeGramsAllowedForTare() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog())
        val productId = db.productDao().insert(TestData.product(name = "Yogur"))
        val dao = db.mealEntryDao()

        val id = dao.insert(
            TestData.mealEntry(productId = productId, productName = "Yogur", grams = -40.5),
        )

        val saved = dao.entriesForDate(TestData.DATE_A).single()
        assertEquals(id, saved.id)
        assertEquals(-40.5, saved.grams, 0.0)
    }

    @Test
    fun entryWithNullProductSucceedsAsQuickCalories() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog())
        val dao = db.mealEntryDao()

        val id = dao.insert(
            TestData.mealEntry(
                productId = null,
                productName = "Calorías rápidas",
                grams = 0.0,
                kcal = 200.0,
                protein = 5.0,
                carbs = 20.0,
                fat = 10.0,
            )
        )

        val saved = dao.entriesForDate(TestData.DATE_A).single()
        assertEquals(id, saved.id)
        kotlin.test.assertNull(saved.productId)
        assertEquals("Calorías rápidas", saved.productName)
        assertEquals(0.0, saved.grams, 0.0)
        assertEquals(200.0, saved.kcal, 0.0)
    }

    @Test
    fun entryWithUnknownProductFails() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog())

        assertFailsWith<SQLiteConstraintException> {
            db.mealEntryDao().insert(
                TestData.mealEntry(productId = 999L, productName = "Fantasma"),
            )
        }
    }

    @Test
    fun entryWithoutDailyLogFails() = runTest {
        val productId = db.productDao().insert(TestData.product(name = "Pan"))

        assertFailsWith<SQLiteConstraintException> {
            db.mealEntryDao().insert(
                TestData.mealEntry(productId = productId, productName = "Pan"),
            )
        }
    }

    @Test
    fun insertAllAndEntriesSinceReturnsCorrectFilteredEntries() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog(date = TestData.DATE_A))
        val productId = db.productDao().insert(TestData.product(name = "Avena"))
        val dao = db.mealEntryDao()

        val list = listOf(
            TestData.mealEntry(date = TestData.DATE_A, productId = productId, productName = "Avena", loggedAt = 1_000L),
            TestData.mealEntry(date = TestData.DATE_A, productId = productId, productName = "Avena", loggedAt = 5_000L),
            TestData.mealEntry(date = TestData.DATE_A, productId = productId, productName = "Avena", loggedAt = 10_000L),
        )
        val ids = dao.insertAll(list)
        assertEquals(3, ids.size)

        val since5000 = dao.entriesSince(5_000L)
        assertEquals(2, since5000.size)
        assertEquals(listOf(10_000L, 5_000L), since5000.map { it.loggedAt })
    }

    @Test
    fun latestDateWithMealBeforeReturnsMostRecentPriorDate() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog(date = "2026-10-10"))
        db.dailyLogDao().insert(TestData.dailyLog(date = "2026-10-12"))
        db.dailyLogDao().insert(TestData.dailyLog(date = "2026-10-14"))
        val productId = db.productDao().insert(TestData.product(name = "Cafe"))
        val dao = db.mealEntryDao()

        dao.insert(TestData.mealEntry(date = "2026-10-10", meal = DefaultMealIds.BREAKFAST, productId = productId, productName = "Cafe"))
        dao.insert(TestData.mealEntry(date = "2026-10-12", meal = DefaultMealIds.BREAKFAST, productId = productId, productName = "Cafe"))
        dao.insert(TestData.mealEntry(date = "2026-10-14", meal = DefaultMealIds.BREAKFAST, productId = productId, productName = "Cafe"))

        val latestBefore14 = dao.latestDateWithMealBefore("2026-10-14", DefaultMealIds.BREAKFAST)
        assertEquals("2026-10-12", latestBefore14)

        val latestBefore10 = dao.latestDateWithMealBefore("2026-10-10", DefaultMealIds.BREAKFAST)
        assertEquals(null, latestBefore10)
    }

    @Test
    fun countForMealReturnsCorrectCount() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog(date = "2026-10-15"))
        val productId = db.productDao().insert(TestData.product(name = "Manzana"))
        val dao = db.mealEntryDao()

        assertEquals(0, dao.countForMeal(DefaultMealIds.BREAKFAST))

        dao.insert(TestData.mealEntry(date = "2026-10-15", meal = DefaultMealIds.BREAKFAST, productId = productId, productName = "Manzana"))
        dao.insert(TestData.mealEntry(date = "2026-10-15", meal = DefaultMealIds.BREAKFAST, productId = productId, productName = "Manzana"))
        dao.insert(TestData.mealEntry(date = "2026-10-15", meal = DefaultMealIds.LUNCH, productId = productId, productName = "Manzana"))

        assertEquals(2, dao.countForMeal(DefaultMealIds.BREAKFAST))
        assertEquals(1, dao.countForMeal(DefaultMealIds.LUNCH))
        assertEquals(0, dao.countForMeal(DefaultMealIds.DINNER))
    }

    @Test
    fun updateLoggedAtForMealUpdatesMatchingEntries() = runTest {
        db.dailyLogDao().insert(TestData.dailyLog(date = "2026-10-15"))
        val productId = db.productDao().insert(TestData.product(name = "Manzana"))
        val dao = db.mealEntryDao()

        dao.insert(TestData.mealEntry(date = "2026-10-15", meal = DefaultMealIds.BREAKFAST, productId = productId, productName = "Manzana", loggedAt = 1000L))
        dao.insert(TestData.mealEntry(date = "2026-10-15", meal = DefaultMealIds.BREAKFAST, productId = productId, productName = "Manzana", loggedAt = 2000L))
        dao.insert(TestData.mealEntry(date = "2026-10-15", meal = DefaultMealIds.LUNCH, productId = productId, productName = "Manzana", loggedAt = 3000L))

        val updatedCount = dao.updateLoggedAtForMeal("2026-10-15", DefaultMealIds.BREAKFAST, 5000L)
        assertEquals(2, updatedCount)

        val breakfastEntries = dao.entriesForDateAndMeal("2026-10-15", DefaultMealIds.BREAKFAST)
        assertEquals(listOf(5000L, 5000L), breakfastEntries.map { it.loggedAt })

        val lunchEntries = dao.entriesForDateAndMeal("2026-10-15", DefaultMealIds.LUNCH)
        assertEquals(listOf(3000L), lunchEntries.map { it.loggedAt })
    }
}
