package com.controlcal.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.controlcal.app.data.db.dao.DailyLogDao
import com.controlcal.app.data.db.dao.GoalProfileDao
import com.controlcal.app.data.db.dao.MealEntryDao
import com.controlcal.app.data.db.dao.ProductDao
import com.controlcal.app.data.db.dao.WeightEntryDao
import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.app.data.db.entity.GoalProfileEntity
import com.controlcal.app.data.db.entity.MealEntryEntity
import com.controlcal.app.data.db.entity.ProductEntity
import com.controlcal.app.data.db.entity.WeightEntryEntity

/**
 * Version 1 = initial schema of section 4 of docs/PLAN.md. The schema is
 * exported to app/schemas and committed so every future change gets a
 * numbered, tested Migration. No fallbackToDestructiveMigration, ever
 * (rule 6 of AGENTS.md).
 */
@Database(
    entities = [
        ProductEntity::class,
        DailyLogEntity::class,
        MealEntryEntity::class,
        WeightEntryEntity::class,
        GoalProfileEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ControlCalDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    abstract fun dailyLogDao(): DailyLogDao

    abstract fun mealEntryDao(): MealEntryDao

    abstract fun weightEntryDao(): WeightEntryDao

    abstract fun goalProfileDao(): GoalProfileDao

    companion object {
        const val NAME = "controlcal.db"
    }
}
