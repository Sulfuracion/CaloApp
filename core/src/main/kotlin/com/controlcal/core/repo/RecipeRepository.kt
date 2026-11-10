package com.controlcal.core.repo

import com.controlcal.core.model.Recipe
import com.controlcal.core.model.RecipeIngredient
import com.controlcal.core.model.ResolvedRecipe

sealed interface RecipeDeleteResult {
    data object Deleted : RecipeDeleteResult
    data class Archived(val hasMealEntries: Boolean, val isUsedAsIngredient: Boolean) : RecipeDeleteResult
}

/**
 * Pure repository interface for managing recipes and recipe ingredients.
 */
interface RecipeRepository {

    suspend fun getAll(includeArchived: Boolean = false): List<ResolvedRecipe>

    suspend fun getById(id: Long): ResolvedRecipe?

    suspend fun getByProductId(productId: Long): ResolvedRecipe?

    /**
     * Saves or updates a recipe and its ingredients in a single atomic transaction.
     * Generates or updates the corresponding Product with source ProductSource.RECIPE.
     */
    suspend fun saveRecipe(
        recipe: Recipe,
        ingredients: List<RecipeIngredient>,
    ): ResolvedRecipe

    /**
     * Deletes or archives a recipe according to safety rules:
     * - If the recipe's product has meal entries in the diary or is an ingredient in another recipe,
     *   both the recipe and product are marked archived.
     * - Otherwise, both are physically deleted.
     */
    suspend fun deleteRecipe(recipeId: Long): RecipeDeleteResult

    /**
     * Recalculates all active recipes that contain [productId] as an ingredient.
     * Propagates in cascade to any other recipes that depend on the updated recipes.
     * Returns the list of recipe IDs that were recalculated.
     */
    suspend fun recalculateRecipesUsingProduct(productId: Long): List<Long>

    /**
     * Returns the list of ingredient product IDs for a given recipe product ID.
     * Used by cycle detection.
     */
    suspend fun getIngredientProductIdsForRecipeProduct(recipeProductId: Long): List<Long>

    suspend fun deleteAll()
}
