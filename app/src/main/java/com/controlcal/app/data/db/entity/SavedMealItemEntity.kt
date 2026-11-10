package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.controlcal.core.model.SavedMealItem

@Entity(
    tableName = "saved_meal_items",
    indices = [
        Index(value = ["saved_meal_id"]),
        Index(value = ["product_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SavedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["saved_meal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
data class SavedMealItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "saved_meal_id")
    val savedMealId: Long,
    val position: Int,
    @ColumnInfo(name = "product_id")
    val productId: Long? = null,
    @ColumnInfo(name = "product_name")
    val productName: String,
    val grams: Double,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
) {
    fun toDomain(): SavedMealItem = SavedMealItem(
        id = id,
        savedMealId = savedMealId,
        position = position,
        productId = productId,
        productName = productName,
        grams = grams,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
    )

    companion object {
        fun fromDomain(domain: SavedMealItem, savedMealId: Long = domain.savedMealId): SavedMealItemEntity = SavedMealItemEntity(
            id = domain.id,
            savedMealId = savedMealId,
            position = domain.position,
            productId = domain.productId,
            productName = domain.productName,
            grams = domain.grams,
            kcal = domain.kcal,
            protein = domain.protein,
            carbs = domain.carbs,
            fat = domain.fat,
        )
    }
}
