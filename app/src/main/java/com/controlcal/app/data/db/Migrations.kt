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
