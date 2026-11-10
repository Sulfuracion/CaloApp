package com.controlcal.app.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.controlcal.core.model.SavedMeal

data class SavedMealWithItemsEntity(
    @Embedded
    val meal: SavedMealEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "saved_meal_id",
    )
    val items: List<SavedMealItemEntity>,
) {
    fun toDomain(): SavedMeal =
        meal.toDomain(items = items.sortedBy { it.position }.map { it.toDomain() })
}
