package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.controlcal.core.model.SavedMeal
import com.controlcal.core.model.SavedMealItem

@Entity(
    tableName = "saved_meals",
    indices = [
        Index(value = ["name"], unique = true),
    ],
)
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "use_count", defaultValue = "0")
    val useCount: Int = 0,
) {
    fun toDomain(items: List<SavedMealItem> = emptyList()): SavedMeal = SavedMeal(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        useCount = useCount,
        items = items,
    )

    companion object {
        fun fromDomain(domain: SavedMeal): SavedMealEntity = SavedMealEntity(
            id = domain.id,
            name = domain.name,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            lastUsedAt = domain.lastUsedAt,
            useCount = domain.useCount,
        )
    }
}
