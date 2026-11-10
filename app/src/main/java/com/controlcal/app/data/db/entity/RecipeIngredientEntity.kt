package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.controlcal.core.model.RecipeIngredient

@Entity(
    tableName = "recipe_ingredients",
    indices = [
        Index(value = ["recipe_id"]),
        Index(value = ["product_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
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
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "recipe_id")
    val recipeId: Long,
    val position: Int,
    @ColumnInfo(name = "product_id")
    val productId: Long,
    val grams: Double,
) {
    fun toDomain(): RecipeIngredient = RecipeIngredient(
        id = id,
        recipeId = recipeId,
        position = position,
        productId = productId,
        grams = grams,
    )
}
