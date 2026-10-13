package com.controlcal.app.data.repo

import com.controlcal.app.data.db.DaoTestBase
import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.Meal
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.nutrition.GetDayTotalsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepositoryMappingTest : DaoTestBase() {

    private lateinit var productRepo: ProductRepositoryImpl
    private lateinit var mealEntryRepo: MealEntryRepositoryImpl
    private lateinit var dailyLogRepo: DailyLogRepositoryImpl
    private lateinit var goalProfileRepo: GoalProfileRepositoryImpl

    @Before
    fun initRepos() {
        productRepo = ProductRepositoryImpl(db.productDao(), db.mealEntryDao())
        mealEntryRepo = MealEntryRepositoryImpl(db.mealEntryDao())
        dailyLogRepo = DailyLogRepositoryImpl(db.dailyLogDao())
        goalProfileRepo = GoalProfileRepositoryImpl(db.goalProfileDao())
    }

    @Test
    fun productRepositoryRoundTripAndArchive() = runBlocking {
        val product = Product(
            name = "Avena Integral",
            kcal100g = 370.0,
            protein100g = 13.5,
            carbs100g = 58.7,
            fat100g = 7.0,
            source = ProductSource.MANUAL,
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        val id = productRepo.insert(product)
        val loaded = productRepo.getById(id)

        assertNotNull(loaded)
        assertEquals("Avena Integral", loaded!!.name)
        assertEquals(370.0, loaded.kcal100g, 0.001)
        assertFalse(loaded.archived)
        assertFalse(productRepo.hasEntries(id))

        // Search by prefix
        val results = productRepo.searchByName("Ave")
        assertEquals(1, results.size)
        assertEquals(id, results.first().id)

        // Archive
        productRepo.archive(id)
        val archivedProduct = productRepo.getById(id)
        assertTrue(archivedProduct!!.archived)
        assertEquals(0, productRepo.getActiveProducts().size)
    }

    @Test
    fun mealEntryRepositoryRoundTripAndTotalsIntegration() = runBlocking {
        // Need parent daily log and product due to foreign keys
        dailyLogRepo.insert(DailyLog(date = "2026-10-06", dayType = DayType.TRAINING))
        val prodId = productRepo.insert(
            Product(
                name = "Yogur",
                kcal100g = 60.0,
                protein100g = 4.0,
                carbs100g = 5.0,
                fat100g = 3.0,
                createdAt = 1000L,
                updatedAt = 1000L,
            ),
        )

        val entry = MealEntry(
            date = "2026-10-06",
            meal = Meal.BREAKFAST,
            productId = prodId,
            productName = "Yogur",
            grams = 125.0,
            kcal = 75.0,
            protein = 5.0,
            carbs = 6.25,
            fat = 3.75,
            loggedAt = 1500L,
        )

        val entryId = mealEntryRepo.insert(entry)
        assertTrue(productRepo.hasEntries(prodId))

        val entries = mealEntryRepo.getByDate("2026-10-06")
        assertEquals(1, entries.size)
        assertEquals(entryId, entries.first().id)
        assertEquals(75.0, entries.first().kcal, 0.001)

        // Integrate with GetDayTotalsUseCase
        val getDayTotals = GetDayTotalsUseCase(mealEntryRepo)
        val dayTotals = getDayTotals("2026-10-06")

        assertEquals(75.0, dayTotals.totals.kcal, 0.001)
        assertEquals(5.0, dayTotals.totals.protein, 0.001)
        assertEquals(1, dayTotals.byMeal[Meal.BREAKFAST]?.entries?.size)

        // Delete entry
        mealEntryRepo.delete(entries.first())
        assertEquals(0, mealEntryRepo.getByDate("2026-10-06").size)
        assertFalse(productRepo.hasEntries(prodId))
    }

    @Test
    fun dailyLogRepositoryUpdatesAndReads() = runBlocking {
        val log = DailyLog(date = "2026-10-06", dayType = DayType.REST, waterMl = 500, notes = "Descanso")
        dailyLogRepo.insert(log)

        var loaded = dailyLogRepo.getByDate("2026-10-06")
        assertNotNull(loaded)
        assertEquals(DayType.REST, loaded!!.dayType)
        assertEquals(500, loaded.waterMl)
        assertEquals("Descanso", loaded.notes)

        dailyLogRepo.setWaterMl("2026-10-06", 1250)
        dailyLogRepo.setDayType("2026-10-06", DayType.TRAINING)

        loaded = dailyLogRepo.getByDate("2026-10-06")
        assertEquals(1250, loaded!!.waterMl)
        assertEquals(DayType.TRAINING, loaded.dayType)
    }

    @Test
    fun goalProfileRepositoryRetrievesActiveProfile() = runBlocking {
        val olderProfile = GoalProfile(
            dayType = DayType.TRAINING,
            kcal = 2400.0,
            proteinG = 160.0,
            carbsG = 260.0,
            fatG = 80.0,
            validFrom = "2026-10-01",
            origin = GoalOrigin.MANUAL,
        )
        val currentProfile = GoalProfile(
            dayType = DayType.TRAINING,
            kcal = 2500.0,
            proteinG = 170.0,
            carbsG = 270.0,
            fatG = 80.0,
            validFrom = "2026-10-05",
            origin = GoalOrigin.MANUAL,
        )
        val futureProfile = GoalProfile(
            dayType = DayType.TRAINING,
            kcal = 2600.0,
            proteinG = 180.0,
            carbsG = 280.0,
            fatG = 80.0,
            validFrom = "2026-10-10",
            origin = GoalOrigin.MANUAL,
        )

        goalProfileRepo.insert(olderProfile)
        goalProfileRepo.insert(currentProfile)
        goalProfileRepo.insert(futureProfile)

        val active = goalProfileRepo.getProfileForDayType(DayType.TRAINING, "2026-10-06")
        assertNotNull(active)
        assertEquals(2500.0, active!!.kcal, 0.001)

        val profilesUpTo = goalProfileRepo.getProfilesForDayTypeUpTo(DayType.TRAINING, "2026-10-06")
        assertEquals(2, profilesUpTo.size)
    }
}
