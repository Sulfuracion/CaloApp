package com.controlcal.app.test

import com.controlcal.core.model.Product
import com.controlcal.core.model.Recipe
import com.controlcal.core.model.RecipeIngredient
import com.controlcal.core.model.ResolvedRecipe
import com.controlcal.core.model.ResolvedRecipeIngredient
import com.controlcal.core.recipe.RecipeCalculator
import com.controlcal.core.repo.RecipeDeleteResult
import com.controlcal.core.repo.RecipeRepository

class FakeRecipeRepository(
    private val productProvider: (Long) -> Product? = { null },
) : RecipeRepository {

    val recipes = mutableListOf<Recipe>()
    val ingredients = mutableListOf<RecipeIngredient>()
    val generatedProducts = mutableMapOf<Long, Product>()
    private var nextRecipeId = 1L
    private var nextProductId = 1000L
    private var nextIngredientId = 1L

    var simulatedMealEntriesCount: (Long) -> Int = { 0 }

    override suspend fun getAll(includeArchived: Boolean): List<ResolvedRecipe> {
        val list = if (includeArchived) recipes else recipes.filter { !it.archived }
        return list.map { resolve(it) }
    }

    override suspend fun getById(id: Long): ResolvedRecipe? {
        val recipe = recipes.find { it.id == id } ?: return null
        return resolve(recipe)
    }

    override suspend fun getByProductId(productId: Long): ResolvedRecipe? {
        val recipe = recipes.find { it.productId == productId } ?: return null
        return resolve(recipe)
    }

    override suspend fun saveRecipe(
        recipe: Recipe,
        ingredients: List<RecipeIngredient>,
    ): ResolvedRecipe {
        val rId = if (recipe.id == 0L) nextRecipeId++ else recipe.id
        val pId = if (recipe.productId == 0L) nextProductId++ else recipe.productId

        val updatedRecipe = recipe.copy(
            id = rId,
            productId = pId,
        )

        recipes.removeAll { it.id == rId }
        recipes.add(updatedRecipe)

        this.ingredients.removeAll { it.recipeId == rId }
        val mappedIngs = ingredients.mapIndexed { index, ing ->
            ing.copy(
                id = if (ing.id == 0L) nextIngredientId++ else ing.id,
                recipeId = rId,
                position = index,
            )
        }
        this.ingredients.addAll(mappedIngs)

        // Calculate and create recipe product
        val resolved = resolve(updatedRecipe)
        val calc = RecipeCalculator.calculate(
            ingredients = resolved.ingredients,
            cookedWeightG = updatedRecipe.cookedWeightG,
            servings = updatedRecipe.servings,
        )
        val prod = RecipeCalculator.buildRecipeProduct(
            recipe = updatedRecipe,
            calculation = calc,
            existingProduct = generatedProducts[pId],
        )
        generatedProducts[pId] = prod

        return resolve(updatedRecipe)
    }

    override suspend fun deleteRecipe(recipeId: Long): RecipeDeleteResult {
        val recipe = recipes.find { it.id == recipeId } ?: return RecipeDeleteResult.Deleted
        val mealEntriesCount = simulatedMealEntriesCount(recipe.productId)
        val isUsedAsIngredient = ingredients.any { it.productId == recipe.productId }

        return if (mealEntriesCount > 0 || isUsedAsIngredient) {
            val idx = recipes.indexOfFirst { it.id == recipeId }
            recipes[idx] = recipe.copy(archived = true)
            RecipeDeleteResult.Archived(
                hasMealEntries = mealEntriesCount > 0,
                isUsedAsIngredient = isUsedAsIngredient,
            )
        } else {
            recipes.removeAll { it.id == recipeId }
            ingredients.removeAll { it.recipeId == recipeId }
            generatedProducts.remove(recipe.productId)
            RecipeDeleteResult.Deleted
        }
    }

    override suspend fun recalculateRecipesUsingProduct(productId: Long): List<Long> {
        val affected = ingredients.filter { it.productId == productId }.map { it.recipeId }.distinct()
        affected.forEach { rId ->
            val r = recipes.find { it.id == rId }
            if (r != null && !r.archived) {
                val res = resolve(r)
                val calc = RecipeCalculator.calculate(
                    ingredients = res.ingredients,
                    cookedWeightG = r.cookedWeightG,
                    servings = r.servings,
                )
                val p = RecipeCalculator.buildRecipeProduct(r, calc, generatedProducts[r.productId])
                generatedProducts[r.productId] = p
            }
        }
        return affected
    }

    override suspend fun getIngredientProductIdsForRecipeProduct(recipeProductId: Long): List<Long> {
        val recipe = recipes.find { it.productId == recipeProductId && !it.archived } ?: return emptyList()
        return ingredients.filter { it.recipeId == recipe.id }.map { it.productId }
    }

    override suspend fun deleteAll() {
        recipes.clear()
        ingredients.clear()
        generatedProducts.clear()
    }

    private fun resolve(recipe: Recipe): ResolvedRecipe {
        val ings = ingredients
            .filter { it.recipeId == recipe.id }
            .sortedBy { it.position }
            .map { ing ->
                val prod = generatedProducts[ing.productId]
                    ?: productProvider(ing.productId)
                    ?: Product(
                        id = ing.productId,
                        name = "Ingrediente ${ing.productId}",
                        kcal100g = 100.0,
                        protein100g = 10.0,
                        carbs100g = 10.0,
                        fat100g = 2.0,
                    )
                ResolvedRecipeIngredient(
                    ingredient = ing,
                    product = prod,
                )
            }
        return ResolvedRecipe(
            recipe = recipe,
            ingredients = ings,
            recipeProduct = generatedProducts[recipe.productId],
        )
    }
}
