package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.controlcal.core.model.ProductSource

/**
 * A food product. Macros are stored per 100 g (or 100 ml when [isLiquid]).
 *
 * A product that has meal entries is never deleted: it is marked [archived]
 * instead (section 4 of docs/PLAN.md).
 */
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["name"]),
    ],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    @ColumnInfo(name = "kcal_100g")
    val kcal100g: Double,
    @ColumnInfo(name = "protein_100g")
    val protein100g: Double,
    @ColumnInfo(name = "carbs_100g")
    val carbs100g: Double,
    @ColumnInfo(name = "fat_100g")
    val fat100g: Double,
    @ColumnInfo(name = "fiber_100g")
    val fiber100g: Double? = null,
    @ColumnInfo(name = "sugars_100g")
    val sugars100g: Double? = null,
    @ColumnInfo(name = "sat_fat_100g")
    val satFat100g: Double? = null,
    @ColumnInfo(name = "salt_100g")
    val salt100g: Double? = null,
    @ColumnInfo(name = "is_liquid")
    val isLiquid: Boolean = false,
    @ColumnInfo(name = "unit_name")
    val unitName: String? = null,
    @ColumnInfo(name = "unit_g")
    val unitG: Double? = null,
    @ColumnInfo(name = "serving_g")
    val servingG: Double? = null,
    @ColumnInfo(name = "min_g")
    val minG: Double? = null,
    @ColumnInfo(name = "max_g")
    val maxG: Double? = null,
    @ColumnInfo(name = "in_pantry")
    val inPantry: Boolean = false,
    val source: ProductSource,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
