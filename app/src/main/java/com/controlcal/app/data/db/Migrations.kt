package com.controlcal.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/**
 * Migration from version 1 to 2 (Task M.6):
 * - Table `meal_entries`: column `product_id` becomes nullable (NULL allowed for quick calories entries).
 * - Foreign keys preserved:
 *     FOREIGN KEY(`date`) REFERENCES `daily_logs`(`date`) ON UPDATE NO ACTION ON DELETE NO ACTION
 *     FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
 * - Indices preserved:
 *     CREATE INDEX IF NOT EXISTS `index_meal_entries_date` ON `meal_entries` (`date`)
 *     CREATE INDEX IF NOT EXISTS `index_meal_entries_product_id_logged_at` ON `meal_entries` (`product_id`, `logged_at`)
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateSql { sql -> db.execSQL(sql) }
    }

    override fun migrate(connection: SQLiteConnection) {
        migrateSql { sql -> connection.execSQL(sql) }
    }

    private inline fun migrateSql(exec: (String) -> Unit) {
        exec(
            """
            CREATE TABLE IF NOT EXISTS `meal_entries_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` TEXT NOT NULL,
                `meal` TEXT NOT NULL,
                `product_id` INTEGER,
                `product_name` TEXT NOT NULL,
                `grams` REAL NOT NULL,
                `kcal` REAL NOT NULL,
                `protein` REAL NOT NULL,
                `carbs` REAL NOT NULL,
                `fat` REAL NOT NULL,
                `logged_at` INTEGER NOT NULL,
                FOREIGN KEY(`date`) REFERENCES `daily_logs`(`date`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )

        exec(
            """
            INSERT INTO `meal_entries_new` (
                `id`, `date`, `meal`, `product_id`, `product_name`,
                `grams`, `kcal`, `protein`, `carbs`, `fat`, `logged_at`
            )
            SELECT
                `id`, `date`, `meal`, `product_id`, `product_name`,
                `grams`, `kcal`, `protein`, `carbs`, `fat`, `logged_at`
            FROM `meal_entries`
            """.trimIndent()
        )

        exec("DROP TABLE `meal_entries`")

        exec("ALTER TABLE `meal_entries_new` RENAME TO `meal_entries`")

        exec("CREATE INDEX IF NOT EXISTS `index_meal_entries_date` ON `meal_entries` (`date`)")
        exec("CREATE INDEX IF NOT EXISTS `index_meal_entries_product_id_logged_at` ON `meal_entries` (`product_id`, `logged_at`)")
    }
}

/**
 * Migration from version 2 to 3 (Task 7.1):
 * - Table `daily_logs`: add column `steps` INTEGER NOT NULL DEFAULT 0.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateSql { sql -> db.execSQL(sql) }
    }

    override fun migrate(connection: SQLiteConnection) {
        migrateSql { sql -> connection.execSQL(sql) }
    }

    private inline fun migrateSql(exec: (String) -> Unit) {
        exec("ALTER TABLE `daily_logs` ADD COLUMN `steps` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migration from version 3 to 4 (Task 3: Mis comidas):
 * - Table `saved_meals`: stores saved meal headers with case-insensitive unique name.
 * - Table `saved_meal_items`: stores individual meal items with CASCADE delete on saved_meal_id.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateSql { sql -> db.execSQL(sql) }
    }

    override fun migrate(connection: SQLiteConnection) {
        migrateSql { sql -> connection.execSQL(sql) }
    }

    private inline fun migrateSql(exec: (String) -> Unit) {
        exec(
            """
            CREATE TABLE IF NOT EXISTS `saved_meals` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL COLLATE NOCASE,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `last_used_at` INTEGER,
                `use_count` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_meals_name` ON `saved_meals` (`name`)")

        exec(
            """
            CREATE TABLE IF NOT EXISTS `saved_meal_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `saved_meal_id` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `product_id` INTEGER,
                `product_name` TEXT NOT NULL,
                `grams` REAL NOT NULL,
                `kcal` REAL,
                `protein` REAL,
                `carbs` REAL,
                `fat` REAL,
                FOREIGN KEY(`saved_meal_id`) REFERENCES `saved_meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )

        exec("CREATE INDEX IF NOT EXISTS `index_saved_meal_items_saved_meal_id` ON `saved_meal_items` (`saved_meal_id`)")
        exec("CREATE INDEX IF NOT EXISTS `index_saved_meal_items_product_id` ON `saved_meal_items` (`product_id`)")
    }
}

/**
 * Migration from version 4 to 5 (Task 6: Vitaminas y suplementos con recordatorios):
 * - Table `supplements`: id, name, dose_amount, dose_unit, schedule_type, weekdays, interval_days,
 *   start_date, times, active, reminders_enabled, notes, created_at, updated_at.
 * - Table `supplement_intakes`: id, supplement_id (FK to supplements CASCADE), scheduled_date, scheduled_time, status, recorded_at.
 * - Unique index on `supplement_intakes(supplement_id, scheduled_date, scheduled_time)`.
 * - Index on `supplement_intakes(scheduled_date)`.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateSql { sql -> db.execSQL(sql) }
    }

    override fun migrate(connection: SQLiteConnection) {
        migrateSql { sql -> connection.execSQL(sql) }
    }

    private inline fun migrateSql(exec: (String) -> Unit) {
        exec(
            """
            CREATE TABLE IF NOT EXISTS `supplements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `dose_amount` REAL NOT NULL,
                `dose_unit` TEXT NOT NULL,
                `schedule_type` TEXT NOT NULL,
                `weekdays` INTEGER,
                `interval_days` INTEGER,
                `start_date` TEXT NOT NULL,
                `times` TEXT NOT NULL,
                `active` INTEGER NOT NULL DEFAULT 1,
                `reminders_enabled` INTEGER NOT NULL DEFAULT 1,
                `notes` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        exec(
            """
            CREATE TABLE IF NOT EXISTS `supplement_intakes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `supplement_id` INTEGER NOT NULL,
                `scheduled_date` TEXT NOT NULL,
                `scheduled_time` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `recorded_at` INTEGER NOT NULL,
                FOREIGN KEY(`supplement_id`) REFERENCES `supplements`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplement_intakes_supplement_id_scheduled_date_scheduled_time` ON `supplement_intakes` (`supplement_id`, `scheduled_date`, `scheduled_time`)")
        exec("CREATE INDEX IF NOT EXISTS `index_supplement_intakes_scheduled_date` ON `supplement_intakes` (`scheduled_date`)")
    }
}
