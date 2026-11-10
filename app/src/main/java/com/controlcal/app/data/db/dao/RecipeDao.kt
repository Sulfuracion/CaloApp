package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.controlcal.app.data.db.entity.RecipeEntity
import com.controlcal.app.data.db.entity.RecipeIngredientEntity
import com.controlcal.app.data.db.entity.RecipeWithIngredientsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes WHERE (:includeArchived = 1 OR archived = 0) ORDER BY name COLLATE NOCASE ASC")
    fun observeAllWithIngredients(includeArchived: Boolean): Flow<List<RecipeWithIngredientsEntity>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE (:includeArchived = 1 OR archived = 0) ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllWithIngredients(includeArchived: Boolean): List<RecipeWithIngredientsEntity>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getWithIngredientsById(id: Long): RecipeWithIngredientsEntity?

    @Transaction
    @Query("SELECT * FROM recipes WHERE product_id = :productId")
    suspend fun getWithIngredientsByProductId(productId: Long): RecipeWithIngredientsEntity?

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE product_id = :productId")
    suspend fun getByProductId(productId: Long): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Long)

    @Query("UPDATE recipes SET archived = :archived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long)

    @Insert
    suspend fun insertIngredients(ingredients: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipe_id = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: Long)

    @Query("SELECT DISTINCT recipe_id FROM recipe_ingredients WHERE product_id = :productId")
    suspend fun getRecipeIdsUsingProduct(productId: Long): List<Long>

    @Query("SELECT product_id FROM recipe_ingredients WHERE recipe_id = :recipeId ORDER BY position ASC")
    suspend fun getIngredientProductIdsForRecipe(recipeId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM recipe_ingredients WHERE product_id = :productId")
    suspend fun countRecipesUsingProduct(productId: Long): Int

    @Query("DELETE FROM recipe_ingredients")
    suspend fun deleteAllIngredients()

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()

    @Query(
        """
        SELECT r.product_id AS recipeProductId, i.product_id AS ingredientProductId
        FROM recipes r
        INNER JOIN recipe_ingredients i ON r.id = i.recipe_id
        WHERE r.archived = 0
        """
    )
    suspend fun getAllActiveRecipeIngredientProductPairs(): List<RecipeIngredientProductPair>

    @Query("SELECT COUNT(*) FROM recipes WHERE archived = 0")
    suspend fun countActive(): Int
}

data class RecipeIngredientProductPair(
    val recipeProductId: Long,
    val ingredientProductId: Long,
)
