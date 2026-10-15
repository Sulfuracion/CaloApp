package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A food entry logged on a day. Macros are copied here when the entry is
 * saved so that editing a product later does not change history
 * (section 4 of docs/PLAN.md). [grams] may be negative in tare mode.
 * [meal] is the stable string identifier of a configured meal.
 */
@Entity(
    tableName = "meal_entries",
    indices = [
        Index(value = ["date"]),
        Index(value = ["product_id", "logged_at"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = DailyLogEntity::class,
            parentColumns = ["date"],
            childColumns = ["date"],
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
        ),
    ],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val meal: String,
    @ColumnInfo(name = "product_id")
    val productId: Long? = null,
    @ColumnInfo(name = "product_name")
    val productName: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    @ColumnInfo(name = "logged_at")
    val loggedAt: Long,
)
