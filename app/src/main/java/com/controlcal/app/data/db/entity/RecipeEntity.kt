package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.controlcal.core.model.Recipe

@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["product_id"], unique = true),
        Index(value = ["name"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "product_id")
    val productId: Long,
    val name: String,
    @ColumnInfo(name = "cooked_weight_g")
    val cookedWeightG: Double? = null,
    val servings: Double? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    val archived: Boolean = false,
) {
    fun toDomain(): Recipe = Recipe(
        id = id,
        productId = productId,
        name = name,
        cookedWeightG = cookedWeightG,
        servings = servings,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archived = archived,
    )
}
