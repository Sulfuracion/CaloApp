package com.controlcal.app.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.controlcal.app.data.db.ControlCalDatabase
import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.app.data.db.entity.GoalProfileEntity
import com.controlcal.app.data.db.entity.MealEntryEntity
import com.controlcal.app.data.db.entity.ProductEntity
import com.controlcal.app.data.db.entity.SavedMealEntity
import com.controlcal.app.data.db.entity.SavedMealItemEntity
import com.controlcal.app.data.db.entity.SupplementEntity
import com.controlcal.app.data.db.entity.SupplementIntakeEntity
import com.controlcal.app.data.db.entity.WeightEntryEntity
import com.controlcal.core.backup.BackupDailyLogDto
import com.controlcal.core.backup.BackupGoalProfileDto
import com.controlcal.core.backup.BackupMealEntryDto
import com.controlcal.core.backup.BackupPayload
import com.controlcal.core.backup.BackupPreferencesDto
import com.controlcal.core.backup.BackupProductDto
import com.controlcal.core.backup.BackupSupplementDto
import com.controlcal.core.backup.BackupSupplementIntakeDto
import com.controlcal.core.backup.BackupWeightEntryDto
import com.controlcal.core.backup.NewerSchemaVersionException
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.ProductSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupManagerRobolectricTest {

    private lateinit var db: ControlCalDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var manager: BackupManager
    private val password = "TestPassword123".toCharArray()

    private val weeklyPatternKey = stringPreferencesKey("default_weekly_pattern")
    private val minKcalLimitKey = doublePreferencesKey("min_kcal_limit")
    private val lastTdeeProposalReviewedDateKey = stringPreferencesKey("last_tdee_proposal_reviewed_date")
    private val configuredMealsKey = stringPreferencesKey("configured_meals_json")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ControlCalDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val prefsFile = File(context.filesDir, "test_backup_prefs.preferences_pb").apply { delete() }
        dataStore = PreferenceDataStoreFactory.create { prefsFile }

        manager = BackupManager(
            database = db,
            productDao = db.productDao(),
            dailyLogDao = db.dailyLogDao(),
            mealEntryDao = db.mealEntryDao(),
            weightEntryDao = db.weightEntryDao(),
            goalProfileDao = db.goalProfileDao(),
            savedMealDao = db.savedMealDao(),
            supplementDao = db.supplementDao(),
            reminderScheduler = com.controlcal.app.reminder.SupplementReminderScheduler(context, db.supplementDao()),
            dataStore = dataStore,
        )
    }

    @After
    fun tearDown() = runTest {
        db.close()
        dataStore.edit { it.clear() }
    }

    @Test
    fun exportAndRestore_matchesAllTablesAndIds_includingNullProductIdAndNegativeTare() = runTest {
        // 1. Seed database with rich data
        val product1 = ProductEntity(
            id = 10L,
            barcode = "8412345678901",
            name = "Avena Copos",
            brand = "Bio",
            kcal100g = 370.0,
            protein100g = 13.5,
            carbs100g = 68.0,
            fat100g = 7.0,
            fiber100g = 10.0,
            isLiquid = false,
            unitName = "ración",
            unitG = 40.0,
            source = ProductSource.MANUAL,
            archived = false,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val product2Archived = ProductEntity(
            id = 20L,
            barcode = null,
            name = "Leche Entera",
            brand = "Vaca",
            kcal100g = 65.0,
            protein100g = 3.2,
            carbs100g = 4.8,
            fat100g = 3.6,
            isLiquid = true,
            source = ProductSource.OFF,
            archived = true,
            createdAt = 2000L,
            updatedAt = 2000L,
        )
        db.productDao().insertAll(listOf(product1, product2Archived))

        val dailyLog = DailyLogEntity(
            date = "2026-11-10",
            dayType = DayType.TRAINING,
            waterMl = 2250,
            notes = "Día de pierna",
            steps = 8400,
        )
        db.dailyLogDao().insert(dailyLog)

        // Meal entries: normal, quick calories (productId = null), tare (negative grams)
        val entry1Normal = MealEntryEntity(
            id = 101L,
            date = "2026-11-10",
            meal = "BREAKFAST",
            productId = 10L,
            productName = "Avena Copos",
            grams = 50.0,
            kcal = 185.0,
            protein = 6.75,
            carbs = 34.0,
            fat = 3.5,
            loggedAt = 1770001000L,
        )
        val entry2QuickKcal = MealEntryEntity(
            id = 102L,
            date = "2026-11-10",
            meal = "LUNCH",
            productId = null, // Quick calories
            productName = "Calorías rápidas",
            grams = 0.0,
            kcal = 300.0,
            protein = 20.0,
            carbs = 30.0,
            fat = 10.0,
            loggedAt = 1770005000L,
        )
        val entry3Tare = MealEntryEntity(
            id = 103L,
            date = "2026-11-10",
            meal = "BREAKFAST",
            productId = 10L,
            productName = "Avena Copos",
            grams = -10.0, // Negative tare
            kcal = -37.0,
            protein = -1.35,
            carbs = -6.8,
            fat = -0.7,
            loggedAt = 1770001500L,
        )
        db.mealEntryDao().insertAll(listOf(entry1Normal, entry2QuickKcal, entry3Tare))

        val weight = WeightEntryEntity(
            date = "2026-11-10",
            weightKg = 79.2,
        )
        db.weightEntryDao().upsert(weight)

        val goalProfile = GoalProfileEntity(
            id = 5L,
            dayType = DayType.TRAINING,
            kcal = 2600.0,
            proteinG = 170.0,
            carbsG = 280.0,
            fatG = 80.0,
            validFrom = "2026-10-01",
            origin = GoalOrigin.MANUAL,
        )
        db.goalProfileDao().insert(goalProfile)

        // Seed DataStore
        dataStore.edit { prefs ->
            prefs[weeklyPatternKey] = "TRAINING,REST,TRAINING,REST,TRAINING,REST,REST"
            prefs[minKcalLimitKey] = 1350.0
            prefs[lastTdeeProposalReviewedDateKey] = "2026-11-03"
            prefs[configuredMealsKey] = """[{"id":"BREAKFAST","name":"Desayuno","startTime":"08:00","isActive":true}]"""
        }

        // Seed saved meals
        val savedMealId = db.savedMealDao().insertMeal(
            SavedMealEntity(
                id = 50L,
                name = "Bowl de avena",
                createdAt = 1000L,
                updatedAt = 1000L,
                lastUsedAt = 2000L,
                useCount = 3,
            )
        )
        db.savedMealDao().insertItems(
            listOf(
                SavedMealItemEntity(
                    id = 501L,
                    savedMealId = savedMealId,
                    position = 0,
                    productId = 10L,
                    productName = "Avena Copos",
                    grams = 50.0,
                )
            )
        )

        val supplement = SupplementEntity(
            id = 70L,
            name = "Vitamina D3",
            doseAmount = 2000.0,
            doseUnit = "UI",
            scheduleType = "DAILY",
            startDate = "2026-11-01",
            times = """["08:00"]""",
            active = true,
            remindersEnabled = true,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        db.supplementDao().insert(supplement)

        val intake = SupplementIntakeEntity(
            id = 701L,
            supplementId = 70L,
            scheduledDate = "2026-11-10",
            scheduledTime = "08:00",
            status = "TAKEN",
            recordedAt = 1000L,
        )
        db.supplementDao().insertIntake(intake)

        // 2. Export to byte stream
        val outStream = ByteArrayOutputStream()
        manager.exportBackup(password, outStream)
        val backupBytes = outStream.toByteArray()
        assertTrue(backupBytes.isNotEmpty())

        // 3. Clear all database tables and DataStore
        db.supplementDao().deleteAllIntakes()
        db.supplementDao().deleteAll()
        db.savedMealDao().deleteAllItems()
        db.savedMealDao().deleteAllMeals()
        db.mealEntryDao().deleteAll()
        db.dailyLogDao().deleteAll()
        db.productDao().deleteAll()
        db.weightEntryDao().deleteAll()
        db.goalProfileDao().deleteAll()
        dataStore.edit { it.clear() }

        assertEquals(0, db.supplementDao().getAll().size)
        assertEquals(0, db.supplementDao().getAllIntakes().size)
        assertEquals(0, db.savedMealDao().getAllWithItems().size)
        assertEquals(0, db.productDao().allIncludingArchived().size)
        assertEquals(0, db.dailyLogDao().getAll().size)
        assertEquals(0, db.mealEntryDao().getAll().size)
        assertEquals(0, db.weightEntryDao().getAllAsc().size)
        assertEquals(0, db.goalProfileDao().getAll().size)

        // 4. Inspect backup and verify summary
        val inStream = ByteArrayInputStream(backupBytes)
        val (summary, payload) = manager.inspectBackup(password, inStream)

        assertEquals(2, summary.productCount)
        assertEquals(1, summary.dayCount)
        assertEquals(3, summary.mealEntryCount)
        assertEquals(1, summary.weightEntryCount)
        assertEquals(5, summary.schemaVersion)
        assertEquals(1, summary.supplementCount)
        assertEquals(1, payload.savedMeals.size)
        assertEquals(1, payload.savedMealItems.size)
        assertEquals(1, payload.supplements.size)
        assertEquals(1, payload.supplementIntakes.size)

        // 5. Restore backup
        manager.restoreBackup(payload)

        // 6. Verify restored data matches exactly
        val restoredProducts = db.productDao().allIncludingArchived()
        assertEquals(2, restoredProducts.size)
        val p1 = restoredProducts.first { it.id == 10L }
        assertEquals("Avena Copos", p1.name)
        assertEquals(10L, p1.id)
        assertFalse(p1.archived)
        val p2 = restoredProducts.first { it.id == 20L }
        assertEquals("Leche Entera", p2.name)
        assertEquals(20L, p2.id)
        assertTrue(p2.archived)

        val restoredLogs = db.dailyLogDao().getAll()
        assertEquals(1, restoredLogs.size)
        assertEquals(8400, restoredLogs.first().steps)
        assertEquals(2250, restoredLogs.first().waterMl)

        val restoredEntries = db.mealEntryDao().getAll()
        assertEquals(3, restoredEntries.size)

        val e1 = restoredEntries.first { it.id == 101L }
        assertEquals(10L, e1.productId)
        assertEquals(50.0, e1.grams, 0.001)

        val e2 = restoredEntries.first { it.id == 102L }
        assertNull(e2.productId) // Null for quick calories preserved!
        assertEquals(0.0, e2.grams, 0.001)
        assertEquals(300.0, e2.kcal, 0.001)

        val e3 = restoredEntries.first { it.id == 103L }
        assertEquals(10L, e3.productId)
        assertEquals(-10.0, e3.grams, 0.001) // Negative tare preserved!
        assertEquals(-37.0, e3.kcal, 0.001)

        val restoredWeight = db.weightEntryDao().getAllAsc()
        assertEquals(1, restoredWeight.size)
        assertEquals(79.2, restoredWeight.first().weightKg, 0.001)

        val restoredGoals = db.goalProfileDao().getAll()
        assertEquals(1, restoredGoals.size)
        assertEquals(5L, restoredGoals.first().id)
        assertEquals(2600.0, restoredGoals.first().kcal, 0.001)

        // Verify restored saved meals and items
        val restoredMeals = db.savedMealDao().getAllWithItems()
        assertEquals(1, restoredMeals.size)
        assertEquals("Bowl de avena", restoredMeals.first().meal.name)
        assertEquals(50L, restoredMeals.first().meal.id)
        assertEquals(3, restoredMeals.first().meal.useCount)
        assertEquals(1, restoredMeals.first().items.size)
        assertEquals(501L, restoredMeals.first().items.first().id)
        assertEquals(50.0, restoredMeals.first().items.first().grams, 0.001)

        // Verify restored supplements and intakes
        val restoredSupplements = db.supplementDao().getAll()
        assertEquals(1, restoredSupplements.size)
        assertEquals("Vitamina D3", restoredSupplements.first().name)
        assertEquals(70L, restoredSupplements.first().id)

        val restoredIntakes = db.supplementDao().getAllIntakes()
        assertEquals(1, restoredIntakes.size)
        assertEquals("TAKEN", restoredIntakes.first().status)
        assertEquals(701L, restoredIntakes.first().id)

        // Verify DataStore preferences restored
        val prefs = dataStore.data.first()
        assertEquals("TRAINING,REST,TRAINING,REST,TRAINING,REST,REST", prefs[weeklyPatternKey])
        assertEquals(1350.0, prefs[minKcalLimitKey] ?: 0.0, 0.001)
        assertEquals("2026-11-03", prefs[lastTdeeProposalReviewedDateKey])
        assertNotNull(prefs[configuredMealsKey])
    }

    @Test
    fun restore_adaptsOlderSchemaVersion1And2_withDefaultSteps() = runTest {
        val payloadV1 = BackupPayload(
            backupFormatVersion = 1,
            schemaVersion = 1, // Schema version 1 had no steps column
            createdAt = 1700000000L,
            appVersion = "1.0",
            dailyLogs = listOf(
                BackupDailyLogDto(
                    date = "2026-10-01",
                    dayType = "REST",
                    waterMl = 1500,
                    notes = null,
                    steps = 9999, // Should be reset to default 0 because schemaVersion < 3
                )
            ),
        )

        manager.restoreBackup(payloadV1)

        val logs = db.dailyLogDao().getAll()
        assertEquals(1, logs.size)
        assertEquals("2026-10-01", logs.first().date)
        assertEquals(0, logs.first().steps) // Adapted with default 0
        assertEquals(1500, logs.first().waterMl)
    }

    @Test
    fun restore_adaptsOlderSchemaVersion3_withoutSavedMeals() = runTest {
        val payloadV3 = BackupPayload(
            backupFormatVersion = 1,
            schemaVersion = 3, // Schema version 3 had no saved_meals
            createdAt = 1700000000L,
            appVersion = "1.0",
            products = listOf(
                BackupProductDto(
                    id = 1L,
                    name = "Arroz",
                    kcal100g = 350.0,
                    protein100g = 7.0,
                    carbs100g = 78.0,
                    fat100g = 1.0,
                    source = "MANUAL",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                )
            ),
        )

        manager.restoreBackup(payloadV3)

        val products = db.productDao().allIncludingArchived()
        assertEquals(1, products.size)
        assertEquals("Arroz", products.first().name)

        val savedMeals = db.savedMealDao().getAllWithItems()
        assertTrue(savedMeals.isEmpty())
    }

    @Test
    fun restore_adaptsOlderSchemaVersion4_withoutSupplements() = runTest {
        val payloadV4 = BackupPayload(
            backupFormatVersion = 1,
            schemaVersion = 4, // Schema version 4 had no supplements
            createdAt = 1700000000L,
            appVersion = "1.0",
            products = listOf(
                BackupProductDto(
                    id = 1L,
                    name = "Arroz",
                    kcal100g = 350.0,
                    protein100g = 7.0,
                    carbs100g = 78.0,
                    fat100g = 1.0,
                    source = "MANUAL",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                )
            ),
        )

        manager.restoreBackup(payloadV4)

        val supplements = db.supplementDao().getAll()
        assertTrue(supplements.isEmpty())
        val intakes = db.supplementDao().getAllIntakes()
        assertTrue(intakes.isEmpty())
    }

    @Test
    fun restore_rejectsNewerSchemaVersion() = runTest {
        val payloadV6 = BackupPayload(
            backupFormatVersion = 1,
            schemaVersion = 6, // Future schema version
            createdAt = 1800000000L,
            appVersion = "2.0",
        )

        try {
            manager.restoreBackup(payloadV6)
            fail("Expected NewerSchemaVersionException")
        } catch (e: NewerSchemaVersionException) {
            assertEquals("Actualiza la app para importar esta copia", e.message)
            assertEquals(6, e.backupSchemaVersion)
            assertEquals(5, e.currentSchemaVersion)
        }
    }

    @Test
    fun restore_rollbackOccursIfExceptionThrown_existingDataRemainsIntact() = runTest {
        // 1. Initial database data
        val initialProduct = ProductEntity(
            id = 1L,
            name = "Manzana",
            kcal100g = 52.0,
            protein100g = 0.3,
            carbs100g = 14.0,
            fat100g = 0.2,
            source = ProductSource.MANUAL,
            createdAt = 100L,
            updatedAt = 100L,
        )
        db.productDao().insert(initialProduct)
        db.dailyLogDao().insert(DailyLogEntity(date = "2026-11-01", dayType = DayType.REST, waterMl = 1000))

        dataStore.edit { it[minKcalLimitKey] = 1200.0 }

        // 2. Prepare corrupted payload with invalid foreign key (meal entry referencing nonexistent dailyLog)
        val corruptPayload = BackupPayload(
            backupFormatVersion = 1,
            schemaVersion = 3,
            createdAt = 1770000000L,
            appVersion = "1.0",
            products = listOf(
                BackupProductDto(
                    id = 99L,
                    name = "Producto Nuevo",
                    kcal100g = 100.0,
                    protein100g = 5.0,
                    carbs100g = 10.0,
                    fat100g = 2.0,
                    source = "MANUAL",
                    createdAt = 200L,
                    updatedAt = 200L,
                )
            ),
            dailyLogs = emptyList(), // No daily logs!
            mealEntries = listOf(
                // This meal entry references date "2026-11-99" which is not in dailyLogs
                BackupMealEntryDto(
                    id = 500L,
                    date = "2026-11-99", // Violates foreign key constraint to daily_logs!
                    meal = "BREAKFAST",
                    productId = 99L,
                    productName = "Producto Nuevo",
                    grams = 100.0,
                    kcal = 100.0,
                    protein = 5.0,
                    carbs = 10.0,
                    fat = 2.0,
                    loggedAt = 5000L,
                )
            ),
            preferences = BackupPreferencesDto(
                minKcalLimit = 9999.0,
            )
        )

        try {
            manager.restoreBackup(corruptPayload)
            fail("Expected restoration to fail due to foreign key or constraint exception")
        } catch (_: Exception) {
            // Expected
        }

        // 3. Verify that rollback preserved the original database state!
        val products = db.productDao().allIncludingArchived()
        assertEquals(1, products.size)
        assertEquals("Manzana", products.first().name)

        val logs = db.dailyLogDao().getAll()
        assertEquals(1, logs.size)
        assertEquals("2026-11-01", logs.first().date)

        val prefs = dataStore.data.first()
        assertEquals(1200.0, prefs[minKcalLimitKey] ?: 0.0, 0.001) // Not overwritten with 9999.0
    }
}
