package com.controlcal.app.data.repo

import com.controlcal.app.data.db.DaoTestBase
import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.model.WeightEntry
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
    private lateinit var weightEntryRepo: WeightEntryRepositoryImpl

    @Before
    fun initRepos() {
        productRepo = ProductRepositoryImpl(db.productDao(), db.mealEntryDao())
        mealEntryRepo = MealEntryRepositoryImpl(db, db.mealEntryDao(), db.dailyLogDao())
        dailyLogRepo = DailyLogRepositoryImpl(db.dailyLogDao())
        goalProfileRepo = GoalProfileRepositoryImpl(db.goalProfileDao())
        weightEntryRepo = WeightEntryRepositoryImpl(db.weightEntryDao())
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

        // Search by substring
        val searchResults = productRepo.search("Integral")
        assertEquals(1, searchResults.size)
        assertEquals(id, searchResults.first().id)

        // Archive
        productRepo.archive(id)
        val archivedProduct = productRepo.getById(id)
        assertTrue(archivedProduct!!.archived)
        assertEquals(0, productRepo.getActiveProducts().size)
    }

    @Test
    fun productRepositoryPantryManagement() = runBlocking {
        val prodId = productRepo.insert(
            Product(
                name = "Huevos camperos",
                kcal100g = 143.0,
                protein100g = 13.0,
                carbs100g = 0.7,
                fat100g = 9.5,
                inPantry = false,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        assertEquals(0, productRepo.getPantryProducts().size)

        // Toggle in pantry
        productRepo.setInPantry(prodId, true)
        val inPantryList = productRepo.getPantryProducts()
        assertEquals(1, inPantryList.size)
        assertEquals(prodId, inPantryList.first().id)
        assertTrue(inPantryList.first().inPantry)

        // Update pantry config (limits & unit)
        productRepo.updatePantryConfig(
            id = prodId,
            inPantry = true,
            minG = 55.0,
            maxG = 165.0,
            unitName = "huevo",
            unitG = 55.0,
        )

        val updated = productRepo.getById(prodId)!!
        assertTrue(updated.inPantry)
        assertEquals(55.0, updated.minG!!, 0.001)
        assertEquals(165.0, updated.maxG!!, 0.001)
        assertEquals("huevo", updated.unitName)
        assertEquals(55.0, updated.unitG!!, 0.001)
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
            meal = DefaultMealIds.BREAKFAST,
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
        assertEquals(1, dayTotals.byMeal[DefaultMealIds.BREAKFAST]?.entries?.size)

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
    fun dailyLogRepositorySetDayTypeCreatesEntryIfNotExists() = runBlocking {
        dailyLogRepo.setDayType("2026-10-18", DayType.TRAINING)
        val loaded = dailyLogRepo.getByDate("2026-10-18")
        assertNotNull(loaded)
        assertEquals(DayType.TRAINING, loaded!!.dayType)
    }

    @Test
    fun dailyLogRepositorySetWaterMlAndSetNotesCreateRowIfNotExists() = runBlocking {
        dailyLogRepo.setWaterMl("2026-10-19", 750)
        val loadedWater = dailyLogRepo.getByDate("2026-10-19")
        assertNotNull(loadedWater)
        assertEquals(750, loadedWater!!.waterMl)

        dailyLogRepo.setNotes("2026-10-20", "Nota sin fila previa")
        val loadedNotes = dailyLogRepo.getByDate("2026-10-20")
        assertNotNull(loadedNotes)
        assertEquals("Nota sin fila previa", loadedNotes!!.notes)
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

    @Test
    fun weightEntryRepositoryRoundTripAndList() = runBlocking {
        val entry1 = WeightEntry(date = "2026-10-01", weightKg = 79.5)
        val entry2 = WeightEntry(date = "2026-10-02", weightKg = 79.2)

        weightEntryRepo.upsert(entry1)
        weightEntryRepo.upsert(entry2)

        val loaded = weightEntryRepo.getByDate("2026-10-01")
        assertNotNull(loaded)
        assertEquals(79.5, loaded!!.weightKg, 0.001)

        val all = weightEntryRepo.getAll()
        assertEquals(2, all.size)
        assertEquals("2026-10-01", all[0].date)
        assertEquals("2026-10-02", all[1].date)

        val between = weightEntryRepo.getBetween("2026-10-02", "2026-10-03")
        assertEquals(1, between.size)
        assertEquals("2026-10-02", between[0].date)

        weightEntryRepo.deleteByDate("2026-10-01")
        assertNull(weightEntryRepo.getByDate("2026-10-01"))
        assertEquals(1, weightEntryRepo.getAll().size)
    }
}

