package com.controlcal.app.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class RecipeWithIngredientsEntity(
    @Embedded
    val recipe: RecipeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "recipe_id",
    )
    val ingredients: List<RecipeIngredientEntity>,
)
