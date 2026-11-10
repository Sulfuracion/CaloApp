package com.controlcal.app.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class ControlCalDatabaseMigrationTest {

    private val TEST_DB: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("migration-test.db").absolutePath

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ControlCalDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesAllRowsTotalsPerDayAndAllowsNullProductId() {
        // Step 1: Create database in version 1
        val dbV1 = helper.createDatabase(TEST_DB, 1)

        // Insert products in v1
        dbV1.execSQL(
            """
            INSERT INTO products (
                id, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                is_liquid, in_pantry, source, archived, created_at, updated_at
            ) VALUES (1, 'Arroz', 350.0, 7.0, 78.0, 1.0, 0, 1, 'MANUAL', 0, 1000, 1000)
            """.trimIndent()
        )
        dbV1.execSQL(
            """
            INSERT INTO products (
                id, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                is_liquid, in_pantry, source, archived, created_at, updated_at
            ) VALUES (2, 'Pollo', 165.0, 31.0, 0.0, 3.6, 0, 1, 'MANUAL', 0, 1000, 1000)
            """.trimIndent()
        )

        // Insert daily_logs for multiple days in v1
        dbV1.execSQL("INSERT INTO daily_logs (date, day_type, water_ml) VALUES ('2026-10-14', 'REST', 1500)")
        dbV1.execSQL("INSERT INTO daily_logs (date, day_type, water_ml) VALUES ('2026-10-15', 'TRAINING', 2000)")

        // Insert meal_entries on multiple days in v1
        // Day 1: Arroz (100g: 350 kcal) + Pollo (200g: 330 kcal) = 680 kcal, 69g P, 78g C, 8.2g F
        dbV1.execSQL(
            """
            INSERT INTO meal_entries (
                id, date, meal, product_id, product_name,
                grams, kcal, protein, carbs, fat, logged_at
            ) VALUES (1, '2026-10-14', 'LUNCH', 1, 'Arroz', 100.0, 350.0, 7.0, 78.0, 1.0, 1000)
            """.trimIndent()
        )
        dbV1.execSQL(
            """
            INSERT INTO meal_entries (
                id, date, meal, product_id, product_name,
                grams, kcal, protein, carbs, fat, logged_at
            ) VALUES (2, '2026-10-14', 'DINNER', 2, 'Pollo', 200.0, 330.0, 62.0, 0.0, 7.2, 2000)
            """.trimIndent()
        )
        // Day 2: Pollo (150g: 247.5 kcal, 46.5g P, 0g C, 5.4g F)
        dbV1.execSQL(
            """
            INSERT INTO meal_entries (
                id, date, meal, product_id, product_name,
                grams, kcal, protein, carbs, fat, logged_at
            ) VALUES (3, '2026-10-15', 'LUNCH', 2, 'Pollo', 150.0, 247.5, 46.5, 0.0, 5.4, 3000)
            """.trimIndent()
        )

        dbV1.close()

        // Step 2: Run migration to v2 and validate schema against 2.json
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Step 3: Validate row counts
        val countCursor = migratedDb.query("SELECT COUNT(*) FROM meal_entries")
        assertTrue(countCursor.moveToFirst())
        val rowCount = countCursor.getInt(0)
        countCursor.close()
        assertEquals(3, rowCount)

        // Validate daily totals per day are identical before and after
        val totalsCursor = migratedDb.query(
            """
            SELECT date, SUM(kcal), SUM(protein), SUM(carbs), SUM(fat)
            FROM meal_entries
            GROUP BY date
            ORDER BY date
            """.trimIndent()
        )

        // Day 1: 2026-10-14
        assertTrue(totalsCursor.moveToNext())
        assertEquals("2026-10-14", totalsCursor.getString(0))
        assertEquals(680.0, totalsCursor.getDouble(1), 0.001)
        assertEquals(69.0, totalsCursor.getDouble(2), 0.001)
        assertEquals(78.0, totalsCursor.getDouble(3), 0.001)
        assertEquals(8.2, totalsCursor.getDouble(4), 0.001)

        // Day 2: 2026-10-15
        assertTrue(totalsCursor.moveToNext())
        assertEquals("2026-10-15", totalsCursor.getString(0))
        assertEquals(247.5, totalsCursor.getDouble(1), 0.001)
        assertEquals(46.5, totalsCursor.getDouble(2), 0.001)
        assertEquals(0.0, totalsCursor.getDouble(3), 0.001)
        assertEquals(5.4, totalsCursor.getDouble(4), 0.001)

        totalsCursor.close()

        // Step 4: Validate that inserting quick calories entry with product_id = NULL succeeds
        migratedDb.execSQL(
            """
            INSERT INTO meal_entries (
                date, meal, product_id, product_name,
                grams, kcal, protein, carbs, fat, logged_at
            ) VALUES ('2026-10-15', 'SNACK', NULL, 'Calorías rápidas', 0.0, 150.0, 5.0, 20.0, 6.0, 4000)
            """.trimIndent()
        )

        val quickEntryCursor = migratedDb.query("SELECT product_id, product_name, grams, kcal FROM meal_entries WHERE product_id IS NULL")
        assertTrue(quickEntryCursor.moveToFirst())
        assertTrue(quickEntryCursor.isNull(0))
        assertEquals("Calorías rápidas", quickEntryCursor.getString(1))
        assertEquals(0.0, quickEntryCursor.getDouble(2), 0.001)
        assertEquals(150.0, quickEntryCursor.getDouble(3), 0.001)
        quickEntryCursor.close()

        // Step 5: Validate foreign keys remain strictly enforced
        migratedDb.execSQL("PRAGMA foreign_keys = ON")
        assertFailsWith<SQLiteConstraintException> {
            migratedDb.execSQL(
                """
                INSERT INTO meal_entries (
                    date, meal, product_id, product_name,
                    grams, kcal, protein, carbs, fat, logged_at
                ) VALUES ('2026-10-15', 'DINNER', 99999, 'Inexistente', 100.0, 100.0, 1.0, 1.0, 1.0, 5000)
                """.trimIndent()
            )
        }
    }

    @Test
    fun migrate2To3PreservesAllRowsAndAddsStepsDefaultZero() {
        // Step 1: Create database in version 2
        val dbV2 = helper.createDatabase(TEST_DB, 2)

        // Insert products and daily_logs in v2
        dbV2.execSQL("INSERT INTO daily_logs (date, day_type, water_ml, notes) VALUES ('2026-10-14', 'REST', 1500, 'Nota 1')")
        dbV2.execSQL("INSERT INTO daily_logs (date, day_type, water_ml, notes) VALUES ('2026-10-15', 'TRAINING', 2000, NULL)")
        dbV2.close()

        // Step 2: Run migration to v3 and validate schema against 3.json
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // Step 3: Verify existing rows have steps default to 0 and all other columns preserved
        val cursor = migratedDb.query("SELECT date, day_type, water_ml, notes, steps FROM daily_logs ORDER BY date ASC")
        assertTrue(cursor.moveToNext())
        assertEquals("2026-10-14", cursor.getString(0))
        assertEquals("REST", cursor.getString(1))
        assertEquals(1500, cursor.getInt(2))
        assertEquals("Nota 1", cursor.getString(3))
        assertEquals(0, cursor.getInt(4))

        assertTrue(cursor.moveToNext())
        assertEquals("2026-10-15", cursor.getString(0))
        assertEquals("TRAINING", cursor.getString(1))
        assertEquals(2000, cursor.getInt(2))
        assertNull(cursor.getString(3))
        assertEquals(0, cursor.getInt(4))
        cursor.close()

        // Step 4: Verify updating and inserting steps works
        migratedDb.execSQL("UPDATE daily_logs SET steps = 8432 WHERE date = '2026-10-14'")
        val updatedCursor = migratedDb.query("SELECT steps FROM daily_logs WHERE date = '2026-10-14'")
        assertTrue(updatedCursor.moveToFirst())
        assertEquals(8432, updatedCursor.getInt(0))
        updatedCursor.close()
    }

    @Test
    fun migrate3To4PreservesExistingTablesAndCreatesSavedMealsTables() {
        // Step 1: Create database in version 3
        val dbV3 = helper.createDatabase(TEST_DB, 3)

        // Insert product
        dbV3.execSQL(
            """
            INSERT INTO products (
                id, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                is_liquid, in_pantry, source, archived, created_at, updated_at
            ) VALUES (1, 'Pasta', 350.0, 12.0, 72.0, 1.5, 0, 1, 'MANUAL', 0, 1000, 1000)
            """.trimIndent()
        )

        // Insert daily log with steps
        dbV3.execSQL("INSERT INTO daily_logs (date, day_type, water_ml, notes, steps) VALUES ('2026-11-10', 'TRAINING', 1750, 'Dia bueno', 9500)")

        // Insert meal entries (normal, tare, and quick calories)
        dbV3.execSQL(
            """
            INSERT INTO meal_entries (
                id, date, meal, product_id, product_name,
                grams, kcal, protein, carbs, fat, logged_at
            ) VALUES (1, '2026-11-10', 'LUNCH', 1, 'Pasta', 100.0, 350.0, 12.0, 72.0, 1.5, 2000)
            """.trimIndent()
        )
        dbV3.execSQL(
            """
            INSERT INTO meal_entries (
                id, date, meal, product_id, product_name,
                grams, kcal, protein, carbs, fat, logged_at
            ) VALUES (2, '2026-11-10', 'LUNCH', 1, 'Pasta', -20.0, -70.0, -2.4, -14.4, -0.3, 2010)
            """.trimIndent()
        )
        dbV3.execSQL(
            """
            INSERT INTO meal_entries (
                id, date, meal, product_id, product_name,
                grams, kcal, protein, carbs, fat, logged_at
            ) VALUES (3, '2026-11-10', 'LUNCH', NULL, 'Calorías rápidas', 0.0, 100.0, 2.0, 15.0, 3.0, 2020)
            """.trimIndent()
        )

        // Insert weight entry and goal profile
        dbV3.execSQL("INSERT INTO weight_entries (date, weight_kg) VALUES ('2026-11-10', 78.5)")
        dbV3.execSQL("INSERT INTO goal_profiles (id, day_type, kcal, protein_g, carbs_g, fat_g, valid_from, origin) VALUES (1, 'TRAINING', 2200.0, 160.0, 250.0, 60.0, '2026-11-01', 'MANUAL')")

        dbV3.close()

        // Step 2: Run migration to v4 and validate schema against 4.json
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        // Step 3: Validate existing tables remain intact
        val productCursor = migratedDb.query("SELECT COUNT(*) FROM products")
        assertTrue(productCursor.moveToFirst())
        assertEquals(1, productCursor.getInt(0))
        productCursor.close()

        val dailyLogCursor = migratedDb.query("SELECT steps FROM daily_logs WHERE date = '2026-11-10'")
        assertTrue(dailyLogCursor.moveToFirst())
        assertEquals(9500, dailyLogCursor.getInt(0))
        dailyLogCursor.close()

        val mealEntriesCursor = migratedDb.query("SELECT COUNT(*) FROM meal_entries")
        assertTrue(mealEntriesCursor.moveToFirst())
        assertEquals(3, mealEntriesCursor.getInt(0))
        mealEntriesCursor.close()

        // Step 4: Insert saved_meals and saved_meal_items
        migratedDb.execSQL(
            """
            INSERT INTO saved_meals (id, name, created_at, updated_at, last_used_at, use_count)
            VALUES (1, 'Almuerzo pasta', 1000, 1000, NULL, 0)
            """.trimIndent()
        )

        migratedDb.execSQL(
            """
            INSERT INTO saved_meal_items (id, saved_meal_id, position, product_id, product_name, grams, kcal, protein, carbs, fat)
            VALUES (1, 1, 0, 1, 'Pasta', 80.0, NULL, NULL, NULL, NULL)
            """.trimIndent()
        )
        migratedDb.execSQL(
            """
            INSERT INTO saved_meal_items (id, saved_meal_id, position, product_id, product_name, grams, kcal, protein, carbs, fat)
            VALUES (2, 1, 1, NULL, 'Calorías rápidas', 0.0, 100.0, 2.0, 15.0, 3.0)
            """.trimIndent()
        )

        val savedMealCursor = migratedDb.query("SELECT name, use_count FROM saved_meals WHERE id = 1")
        assertTrue(savedMealCursor.moveToFirst())
        assertEquals("Almuerzo pasta", savedMealCursor.getString(0))
        assertEquals(0, savedMealCursor.getInt(1))
        savedMealCursor.close()

        val itemsCursor = migratedDb.query("SELECT COUNT(*) FROM saved_meal_items WHERE saved_meal_id = 1")
        assertTrue(itemsCursor.moveToFirst())
        assertEquals(2, itemsCursor.getInt(0))
        itemsCursor.close()

        // Step 5: Validate case-insensitive unique constraint on saved_meals.name
        assertFailsWith<SQLiteConstraintException> {
            migratedDb.execSQL(
                """
                INSERT INTO saved_meals (id, name, created_at, updated_at, last_used_at, use_count)
                VALUES (2, 'almuerzo PASTA', 2000, 2000, NULL, 0)
                """.trimIndent()
            )
        }

        // Step 6: Validate ON DELETE CASCADE
        migratedDb.execSQL("PRAGMA foreign_keys = ON")
        migratedDb.execSQL("DELETE FROM saved_meals WHERE id = 1")

        val itemsAfterDeleteCursor = migratedDb.query("SELECT COUNT(*) FROM saved_meal_items WHERE saved_meal_id = 1")
        assertTrue(itemsAfterDeleteCursor.moveToFirst())
        assertEquals(0, itemsAfterDeleteCursor.getInt(0))
        itemsAfterDeleteCursor.close()
    }

    @Test
    fun migrate4To5PreservesAllExistingTablesAndCreatesSupplementsAndIntakesTables() {
        // Step 1: Create database in version 4
        val dbV4 = helper.createDatabase(TEST_DB, 4)

        // Seed v4 data
        dbV4.execSQL(
            """
            INSERT INTO products (
                id, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                is_liquid, in_pantry, source, archived, created_at, updated_at
            ) VALUES (1, 'Arroz', 350.0, 7.0, 78.0, 1.0, 0, 1, 'MANUAL', 0, 1000, 1000)
            """.trimIndent()
        )
        dbV4.execSQL("INSERT INTO daily_logs (date, day_type, water_ml, steps) VALUES ('2026-11-10', 'TRAINING', 2000, 9500)")
        dbV4.execSQL(
            """
            INSERT INTO meal_entries (
                id, date, meal, product_id, product_name, grams, kcal, protein, carbs, fat, logged_at
            ) VALUES (1, '2026-11-10', 'LUNCH', 1, 'Arroz', 100.0, 350.0, 7.0, 78.0, 1.0, 1000)
            """.trimIndent()
        )
        dbV4.execSQL(
            """
            INSERT INTO saved_meals (id, name, created_at, updated_at, last_used_at, use_count)
            VALUES (1, 'Almuerzo arroz', 1000, 1000, NULL, 0)
            """.trimIndent()
        )
        dbV4.execSQL(
            """
            INSERT INTO saved_meal_items (id, saved_meal_id, position, product_id, product_name, grams, kcal, protein, carbs, fat)
            VALUES (1, 1, 0, 1, 'Arroz', 100.0, NULL, NULL, NULL, NULL)
            """.trimIndent()
        )
        dbV4.close()

        // Step 2: Migrate to version 5
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        // Step 3: Verify existing data preserved intact
        val productCursor = migratedDb.query("SELECT name FROM products WHERE id = 1")
        assertTrue(productCursor.moveToFirst())
        assertEquals("Arroz", productCursor.getString(0))
        productCursor.close()

        val dailyLogCursor = migratedDb.query("SELECT steps FROM daily_logs WHERE date = '2026-11-10'")
        assertTrue(dailyLogCursor.moveToFirst())
        assertEquals(9500, dailyLogCursor.getInt(0))
        dailyLogCursor.close()

        val mealEntriesCursor = migratedDb.query("SELECT COUNT(*) FROM meal_entries")
        assertTrue(mealEntriesCursor.moveToFirst())
        assertEquals(1, mealEntriesCursor.getInt(0))
        mealEntriesCursor.close()

        val savedMealsCursor = migratedDb.query("SELECT COUNT(*) FROM saved_meals")
        assertTrue(savedMealsCursor.moveToFirst())
        assertEquals(1, savedMealsCursor.getInt(0))
        savedMealsCursor.close()

        // Step 4: Insert into supplements and supplement_intakes
        migratedDb.execSQL(
            """
            INSERT INTO supplements (
                id, name, dose_amount, dose_unit, schedule_type, weekdays, interval_days,
                start_date, times, active, reminders_enabled, notes, created_at, updated_at
            ) VALUES (1, 'Creatina', 5.0, 'g', 'DAILY', NULL, NULL, '2026-11-01', '["09:00"]', 1, 1, 'Post entreno', 1000, 1000)
            """.trimIndent()
        )

        migratedDb.execSQL(
            """
            INSERT INTO supplement_intakes (
                id, supplement_id, scheduled_date, scheduled_time, status, recorded_at
            ) VALUES (1, 1, '2026-11-10', '09:00', 'TAKEN', 1000)
            """.trimIndent()
        )

        val suppCursor = migratedDb.query("SELECT name, dose_unit, times FROM supplements WHERE id = 1")
        assertTrue(suppCursor.moveToFirst())
        assertEquals("Creatina", suppCursor.getString(0))
        assertEquals("g", suppCursor.getString(1))
        assertEquals("[\"09:00\"]", suppCursor.getString(2))
        suppCursor.close()

        val intakeCursor = migratedDb.query("SELECT status FROM supplement_intakes WHERE id = 1")
        assertTrue(intakeCursor.moveToFirst())
        assertEquals("TAKEN", intakeCursor.getString(0))
        intakeCursor.close()

        // Step 5: Verify unique constraint on (supplement_id, scheduled_date, scheduled_time)
        assertFailsWith<SQLiteConstraintException> {
            migratedDb.execSQL(
                """
                INSERT INTO supplement_intakes (
                    id, supplement_id, scheduled_date, scheduled_time, status, recorded_at
                ) VALUES (2, 1, '2026-11-10', '09:00', 'SKIPPED', 2000)
                """.trimIndent()
            )
        }

        // Step 6: Verify ON DELETE CASCADE
        migratedDb.execSQL("PRAGMA foreign_keys = ON")
        migratedDb.execSQL("DELETE FROM supplements WHERE id = 1")

        val intakesAfterDelete = migratedDb.query("SELECT COUNT(*) FROM supplement_intakes WHERE supplement_id = 1")
        assertTrue(intakesAfterDelete.moveToFirst())
        assertEquals(0, intakesAfterDelete.getInt(0))
        intakesAfterDelete.close()
    }

    @Test
    fun migrate5To6PreservesAllRowsAndAddsRecipeTables() {
        // Step 1: Create database in version 5
        val dbV5 = helper.createDatabase(TEST_DB, 5)

        dbV5.execSQL(
            """
            INSERT INTO products (
                id, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                is_liquid, in_pantry, source, archived, created_at, updated_at
            ) VALUES (1, 'Arroz', 350.0, 7.0, 78.0, 1.0, 0, 1, 'MANUAL', 0, 1000, 1000)
            """.trimIndent()
        )
        dbV5.execSQL(
            """
            INSERT INTO products (
                id, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                is_liquid, in_pantry, source, archived, created_at, updated_at
            ) VALUES (2, 'Pollo', 165.0, 31.0, 0.0, 3.6, 0, 1, 'MANUAL', 0, 1000, 1000)
            """.trimIndent()
        )
        dbV5.execSQL("INSERT INTO daily_logs (date, day_type, water_ml, steps) VALUES ('2026-11-10', 'REST', 1500, 8000)")
        dbV5.execSQL(
            """
            INSERT INTO meal_entries (
                id, date, meal, product_id, product_name,
                grams, kcal, protein, carbs, fat, logged_at
            ) VALUES (1, '2026-11-10', 'LUNCH', 1, 'Arroz', 100.0, 350.0, 7.0, 78.0, 1.0, 1000)
            """.trimIndent()
        )
        dbV5.execSQL("INSERT INTO saved_meals (id, name, created_at, updated_at, use_count) VALUES (1, 'Almuerzo habitual', 1000, 1000, 0)")
        dbV5.execSQL(
            """
            INSERT INTO supplements (
                id, name, dose_amount, dose_unit, schedule_type, start_date, times, active, reminders_enabled, created_at, updated_at
            ) VALUES (1, 'Vitamina D3', 2000.0, 'UI', 'DAILY', '2026-11-01', '["09:00"]', 1, 1, 1000, 1000)
            """.trimIndent()
        )
        dbV5.close()

        // Step 2: Migrate to version 6 and validate against schema 6.json
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        // Step 3: Verify existing data preserved
        val prodCursor = migratedDb.query("SELECT COUNT(*) FROM products")
        assertTrue(prodCursor.moveToFirst())
        assertEquals(2, prodCursor.getInt(0))
        prodCursor.close()

        val stepsCursor = migratedDb.query("SELECT steps FROM daily_logs WHERE date = '2026-11-10'")
        assertTrue(stepsCursor.moveToFirst())
        assertEquals(8000, stepsCursor.getInt(0))
        stepsCursor.close()

        val suppCursor = migratedDb.query("SELECT name FROM supplements WHERE id = 1")
        assertTrue(suppCursor.moveToFirst())
        assertEquals("Vitamina D3", suppCursor.getString(0))
        suppCursor.close()

        // Step 4: Insert a recipe product with source = 'RECIPE'
        migratedDb.execSQL(
            """
            INSERT INTO products (
                id, name, kcal_100g, protein_100g, carbs_100g, fat_100g,
                is_liquid, unit_name, unit_g, serving_g, in_pantry, source, archived, created_at, updated_at
            ) VALUES (3, 'Pollo con arroz', 150.0, 15.0, 20.0, 2.0, 0, 'ración', 250.0, 250.0, 0, 'RECIPE', 0, 2000, 2000)
            """.trimIndent()
        )

        // Step 5: Insert recipe and ingredients
        migratedDb.execSQL(
            """
            INSERT INTO recipes (
                id, product_id, name, cooked_weight_g, servings, notes, created_at, updated_at, archived
            ) VALUES (1, 3, 'Pollo con arroz', 500.0, 2.0, 'Receta casera', 2000, 2000, 0)
            """.trimIndent()
        )

        migratedDb.execSQL(
            """
            INSERT INTO recipe_ingredients (
                id, recipe_id, position, product_id, grams
            ) VALUES (1, 1, 0, 2, 200.0)
            """.trimIndent()
        )
        migratedDb.execSQL(
            """
            INSERT INTO recipe_ingredients (
                id, recipe_id, position, product_id, grams
            ) VALUES (2, 1, 1, 1, 100.0)
            """.trimIndent()
        )

        val recipeCursor = migratedDb.query("SELECT name, cooked_weight_g, servings FROM recipes WHERE id = 1")
        assertTrue(recipeCursor.moveToFirst())
        assertEquals("Pollo con arroz", recipeCursor.getString(0))
        assertEquals(500.0, recipeCursor.getDouble(1), 0.001)
        assertEquals(2.0, recipeCursor.getDouble(2), 0.001)
        recipeCursor.close()

        val ingsCursor = migratedDb.query("SELECT COUNT(*) FROM recipe_ingredients WHERE recipe_id = 1")
        assertTrue(ingsCursor.moveToFirst())
        assertEquals(2, ingsCursor.getInt(0))
        ingsCursor.close()

        // Step 6: Verify unique constraint on product_id
        assertFailsWith<SQLiteConstraintException> {
            migratedDb.execSQL(
                """
                INSERT INTO recipes (
                    id, product_id, name, created_at, updated_at, archived
                ) VALUES (2, 3, 'Duplicado', 3000, 3000, 0)
                """.trimIndent()
            )
        }

        // Step 7: Verify CASCADE delete on recipe_ingredients
        migratedDb.execSQL("PRAGMA foreign_keys = ON")
        migratedDb.execSQL("DELETE FROM recipes WHERE id = 1")

        val ingsAfterDelete = migratedDb.query("SELECT COUNT(*) FROM recipe_ingredients WHERE recipe_id = 1")
        assertTrue(ingsAfterDelete.moveToFirst())
        assertEquals(0, ingsAfterDelete.getInt(0))
        ingsAfterDelete.close()
    }
}
