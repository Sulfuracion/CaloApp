package com.controlcal.core.model

/**
 * Domain model representing a homemade recipe.
 *
 * Each recipe generates and maintains an associated [Product] with [ProductSource.RECIPE],
 * allowing it to seamlessly integrate into search, portion logging, pantry, and Macro Tetris.
 */
data class Recipe(
    val id: Long = 0,
    val productId: Long = 0,
    val name: String,
    val cookedWeightG: Double? = null,
    val servings: Double? = null,
    val notes: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val archived: Boolean = false,
)

/**
 * Domain model representing an ingredient within a recipe.
 */
data class RecipeIngredient(
    val id: Long = 0,
    val recipeId: Long = 0,
    val position: Int,
    val productId: Long,
    val grams: Double,
)

/**
 * An ingredient joined with its resolved [Product].
 */
data class ResolvedRecipeIngredient(
    val ingredient: RecipeIngredient,
    val product: Product,
) {
    val productId: Long get() = ingredient.productId
    val grams: Double get() = ingredient.grams
    val position: Int get() = ingredient.position
}

/**
 * A recipe with its full list of resolved ingredients and its associated recipe product.
 */
data class ResolvedRecipe(
    val recipe: Recipe,
    val ingredients: List<ResolvedRecipeIngredient>,
    val recipeProduct: Product? = null,
) {
    val id: Long get() = recipe.id
    val name: String get() = recipe.name
    val productId: Long get() = recipe.productId
    val cookedWeightG: Double? get() = recipe.cookedWeightG
    val servings: Double? get() = recipe.servings
    val notes: String? get() = recipe.notes
    val archived: Boolean get() = recipe.archived
}
