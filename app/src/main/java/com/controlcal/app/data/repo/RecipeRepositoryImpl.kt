package com.controlcal.app.data.repo

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.controlcal.app.data.db.ControlCalDatabase
import com.controlcal.app.data.db.dao.MealEntryDao
import com.controlcal.app.data.db.dao.ProductDao
import com.controlcal.app.data.db.dao.RecipeDao
import com.controlcal.app.data.db.entity.RecipeEntity
import com.controlcal.app.data.db.entity.RecipeIngredientEntity
import com.controlcal.app.data.db.entity.RecipeWithIngredientsEntity
import com.controlcal.core.model.Recipe
import com.controlcal.core.model.RecipeIngredient
import com.controlcal.core.model.ResolvedRecipe
import com.controlcal.core.model.ResolvedRecipeIngredient
import com.controlcal.core.recipe.RecipeCalculator
import com.controlcal.core.recipe.RecipeCycleDetector
import com.controlcal.core.repo.RecipeDeleteResult
import com.controlcal.core.repo.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val database: ControlCalDatabase,
    private val recipeDao: RecipeDao,
    private val productDao: ProductDao,
    private val mealEntryDao: MealEntryDao,
) : RecipeRepository {

    fun observeAll(includeArchived: Boolean = false): Flow<List<ResolvedRecipe>> =
        recipeDao.observeAllWithIngredients(includeArchived).map { list ->
            list.map { it.toResolved() }
        }

    override suspend fun getAll(includeArchived: Boolean): List<ResolvedRecipe> {
        val list = recipeDao.getAllWithIngredients(includeArchived)
        return list.map { it.toResolved() }
    }

    override suspend fun getById(id: Long): ResolvedRecipe? {
        val entity = recipeDao.getWithIngredientsById(id) ?: return null
        return entity.toResolved()
    }

    override suspend fun getByProductId(productId: Long): ResolvedRecipe? {
        val entity = recipeDao.getWithIngredientsByProductId(productId) ?: return null
        return entity.toResolved()
    }

    override suspend fun saveRecipe(
        recipe: Recipe,
        ingredients: List<RecipeIngredient>,
    ): ResolvedRecipe {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val now = System.currentTimeMillis()

                // Check for cycles
                val pairs = recipeDao.getAllActiveRecipeIngredientProductPairs()
                val graph = pairs.groupBy({ it.recipeProductId }, { it.ingredientProductId })

                for (ing in ingredients) {
                    val isCycle = RecipeCycleDetector.wouldCreateCycle(
                        targetRecipeProductId = recipe.productId,
                        candidateIngredientProductId = ing.productId,
                        ingredientProductIdsProvider = { graph[it] ?: emptyList() },
                    )
                    if (isCycle) {
                        throw IllegalArgumentException("Circular dependency detected for ingredient product ID ${ing.productId}")
                    }
                }

                // Resolve products for ingredients
                val ingProductIds = ingredients.map { it.productId }.distinct()
                val productsMap = if (ingProductIds.isNotEmpty()) {
                    productDao.getByIds(ingProductIds).associateBy { it.id }
                } else {
                    emptyMap()
                }

                val resolvedIngredients = ingredients.mapIndexed { index, ing ->
                    val productEntity = productsMap[ing.productId]
                        ?: throw IllegalStateException("Ingredient product not found with ID ${ing.productId}")
                    ResolvedRecipeIngredient(
                        ingredient = ing.copy(position = index),
                        product = productEntity.toDomain(),
                    )
                }

                // Calculate nutritional values
                val calculation = RecipeCalculator.calculate(
                    ingredients = resolvedIngredients,
                    cookedWeightG = recipe.cookedWeightG,
                    servings = recipe.servings,
                )

                val (recipeId, finalProductId) = if (recipe.productId == 0L) {
                    // Create associated Product first
                    val newProduct = RecipeCalculator.buildRecipeProduct(
                        recipe = recipe,
                        calculation = calculation,
                        existingProduct = null,
                        currentTimeMillis = now,
                    )
                    val prodId = productDao.insert(newProduct.toEntity())

                    val recipeEntity = RecipeEntity(
                        id = 0L,
                        productId = prodId,
                        name = recipe.name.trim(),
                        cookedWeightG = recipe.cookedWeightG,
                        servings = recipe.servings,
                        notes = recipe.notes?.trim()?.ifEmpty { null },
                        createdAt = if (recipe.createdAt > 0) recipe.createdAt else now,
                        updatedAt = now,
                        archived = recipe.archived,
                    )
                    val rId = recipeDao.insertRecipe(recipeEntity)
                    Pair(rId, prodId)
                } else {
                    // Update existing Product
                    val existingProduct = productDao.getById(recipe.productId)?.toDomain()
                    val updatedProduct = RecipeCalculator.buildRecipeProduct(
                        recipe = recipe,
                        calculation = calculation,
                        existingProduct = existingProduct,
                        currentTimeMillis = now,
                    )
                    productDao.update(updatedProduct.toEntity())

                    val recipeEntity = RecipeEntity(
                        id = recipe.id,
                        productId = recipe.productId,
                        name = recipe.name.trim(),
                        cookedWeightG = recipe.cookedWeightG,
                        servings = recipe.servings,
                        notes = recipe.notes?.trim()?.ifEmpty { null },
                        createdAt = if (recipe.createdAt > 0) recipe.createdAt else now,
                        updatedAt = now,
                        archived = recipe.archived,
                    )
                    recipeDao.updateRecipe(recipeEntity)
                    Pair(recipe.id, recipe.productId)
                }

                // Replace ingredients
                recipeDao.deleteIngredientsForRecipe(recipeId)
                val ingredientEntities = resolvedIngredients.map { resolved ->
                    RecipeIngredientEntity(
                        id = 0L,
                        recipeId = recipeId,
                        position = resolved.position,
                        productId = resolved.productId,
                        grams = resolved.grams,
                    )
                }
                if (ingredientEntities.isNotEmpty()) {
                    recipeDao.insertIngredients(ingredientEntities)
                }

                // If this recipe's product is used in other recipes, cascade recalculation
                recalculateRecipesUsingProductInternal(finalProductId)

                val savedEntity = recipeDao.getWithIngredientsById(recipeId)
                    ?: throw IllegalStateException("Failed to load saved recipe $recipeId")
                savedEntity.toResolved()
            }
        }
    }

    override suspend fun deleteRecipe(recipeId: Long): RecipeDeleteResult {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val recipe = recipeDao.getById(recipeId) ?: return@immediateTransaction RecipeDeleteResult.Deleted
                val hasMealEntries = mealEntryDao.countByProductId(recipe.productId) > 0
                val isUsedAsIngredient = recipeDao.countRecipesUsingProduct(recipe.productId) > 0

                val now = System.currentTimeMillis()
                if (hasMealEntries || isUsedAsIngredient) {
                    // Archive to preserve referential integrity
                    recipeDao.setArchived(recipeId, true, now)
                    productDao.archive(recipe.productId)
                    RecipeDeleteResult.Archived(
                        hasMealEntries = hasMealEntries,
                        isUsedAsIngredient = isUsedAsIngredient,
                    )
                } else {
                    // Safe to physically delete
                    recipeDao.deleteIngredientsForRecipe(recipeId)
                    recipeDao.deleteRecipeById(recipeId)
                    productDao.deleteById(recipe.productId)
                    RecipeDeleteResult.Deleted
                }
            }
        }
    }

    override suspend fun recalculateRecipesUsingProduct(productId: Long): List<Long> {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                recalculateRecipesUsingProductInternal(productId)
            }
        }
    }

    override suspend fun getIngredientProductIdsForRecipeProduct(recipeProductId: Long): List<Long> {
        val recipe = recipeDao.getByProductId(recipeProductId) ?: return emptyList()
        return recipeDao.getIngredientProductIdsForRecipe(recipe.id)
    }

    override suspend fun deleteAll() {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                recipeDao.deleteAllIngredients()
                recipeDao.deleteAll()
            }
        }
    }

    private suspend fun recalculateRecipesUsingProductInternal(
        productId: Long,
        recalculated: MutableSet<Long> = mutableSetOf(),
    ): List<Long> {
        val recipeIds = recipeDao.getRecipeIdsUsingProduct(productId)
        val now = System.currentTimeMillis()

        for (rId in recipeIds) {
            if (recalculated.contains(rId)) continue
            recalculated.add(rId)

            val rWithIngredients = recipeDao.getWithIngredientsById(rId) ?: continue
            if (rWithIngredients.recipe.archived) continue

            val ingProductIds = rWithIngredients.ingredients.map { it.productId }.distinct()
            val productsMap = if (ingProductIds.isNotEmpty()) {
                productDao.getByIds(ingProductIds).associateBy { it.id }
            } else {
                emptyMap()
            }

            val resolvedIngredients = rWithIngredients.ingredients.mapNotNull { ing ->
                val prod = productsMap[ing.productId] ?: return@mapNotNull null
                ResolvedRecipeIngredient(
                    ingredient = ing.toDomain(),
                    product = prod.toDomain(),
                )
            }

            val calculation = RecipeCalculator.calculate(
                ingredients = resolvedIngredients,
                cookedWeightG = rWithIngredients.recipe.cookedWeightG,
                servings = rWithIngredients.recipe.servings,
            )

            val existingProduct = productDao.getById(rWithIngredients.recipe.productId)?.toDomain()
            if (existingProduct != null) {
                val updatedProduct = RecipeCalculator.buildRecipeProduct(
                    recipe = rWithIngredients.recipe.toDomain(),
                    calculation = calculation,
                    existingProduct = existingProduct,
                    currentTimeMillis = now,
                )
                productDao.update(updatedProduct.toEntity())
            }

            // Recurse down any recipes that use this recipe as an ingredient
            recalculateRecipesUsingProductInternal(rWithIngredients.recipe.productId, recalculated)
        }

        return recalculated.toList()
    }

    private suspend fun RecipeWithIngredientsEntity.toResolved(): ResolvedRecipe {
        val ingProductIds = ingredients.map { it.productId }.distinct()
        val productsMap = if (ingProductIds.isNotEmpty()) {
            productDao.getByIds(ingProductIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        val resolvedIngredients = ingredients.sortedBy { it.position }.mapNotNull { ing ->
            val productEntity = productsMap[ing.productId] ?: return@mapNotNull null
            ResolvedRecipeIngredient(
                ingredient = ing.toDomain(),
                product = productEntity.toDomain(),
            )
        }

        val recipeProductEntity = productDao.getById(recipe.productId)

        return ResolvedRecipe(
            recipe = recipe.toDomain(),
            ingredients = resolvedIngredients,
            recipeProduct = recipeProductEntity?.toDomain(),
        )
    }
}
