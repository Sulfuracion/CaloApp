package com.controlcal.app.data.db

import android.database.sqlite.SQLiteConstraintException
import com.controlcal.core.model.Meal
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

        dao.insert(TestData.mealEntry(meal = Meal.BREAKFAST, productId = productId, productName = "Huevo"))
        dao.insert(TestData.mealEntry(meal = Meal.DINNER, productId = productId, productName = "Huevo", loggedAt = 2_000L))

        assertEquals(1, dao.entriesForDateAndMeal(TestData.DATE_A, Meal.BREAKFAST).size)
        assertEquals(1, dao.entriesForDateAndMeal(TestData.DATE_A, Meal.DINNER).size)
        assertEquals(0, dao.entriesForDateAndMeal(TestData.DATE_A, Meal.SNACK).size)
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
}
