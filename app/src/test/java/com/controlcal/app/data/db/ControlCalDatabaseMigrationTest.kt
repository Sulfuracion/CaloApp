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
}
