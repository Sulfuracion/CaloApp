package com.controlcal.app.data.db

import android.database.sqlite.SQLiteConstraintException
import com.controlcal.app.data.db.dao.ProductDao
import com.controlcal.app.data.db.dao.RecipeDao
import com.controlcal.app.data.db.entity.RecipeEntity
import com.controlcal.app.data.db.entity.RecipeIngredientEntity
import com.controlcal.core.model.ProductSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class RecipeDaoTest : DaoTestBase() {

    private val recipeDao: RecipeDao
        get() = db.recipeDao()

    private val productDao: ProductDao
        get() = db.productDao()

    @Test
    fun insertAndReadRecipeWithIngredients() = runTest {
        val prodIng1 = productDao.insert(TestData.product(barcode = null, name = "Pollo", kcal100g = 120.0))
        val prodIng2 = productDao.insert(TestData.product(barcode = null, name = "Arroz", kcal100g = 350.0))
        val prodRecipe = productDao.insert(
            TestData.product(
                barcode = null,
                name = "Pollo con arroz",
                kcal100g = 200.0,
                source = ProductSource.RECIPE,
            )
        )

        val recipeId = recipeDao.insertRecipe(
            RecipeEntity(
                productId = prodRecipe,
                name = "Pollo con arroz",
                cookedWeightG = 400.0,
                servings = 2.0,
                notes = "Olla express",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        assertTrue(recipeId > 0)

        recipeDao.insertIngredients(
            listOf(
                RecipeIngredientEntity(recipeId = recipeId, position = 0, productId = prodIng1, grams = 200.0),
                RecipeIngredientEntity(recipeId = recipeId, position = 1, productId = prodIng2, grams = 100.0),
            )
        )

        val retrieved = recipeDao.getWithIngredientsById(recipeId)
        assertNotNull(retrieved)
        assertEquals("Pollo con arroz", retrieved!!.recipe.name)
        assertEquals(400.0, retrieved.recipe.cookedWeightG!!, 0.001)
        assertEquals(2.0, retrieved.recipe.servings!!, 0.001)
        assertEquals(2, retrieved.ingredients.size)
        assertEquals(prodIng1, retrieved.ingredients[0].productId)
        assertEquals(200.0, retrieved.ingredients[0].grams, 0.001)
    }

    @Test
    fun uniqueProductIdConstraintThrowsException() = runTest {
        val prodRecipe = productDao.insert(TestData.product(barcode = null, name = "Receta 1", source = ProductSource.RECIPE))

        recipeDao.insertRecipe(
            RecipeEntity(
                productId = prodRecipe,
                name = "Receta 1",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        assertFailsWith<SQLiteConstraintException> {
            recipeDao.insertRecipe(
                RecipeEntity(
                    productId = prodRecipe, // Duplicate product_id
                    name = "Receta 2",
                    createdAt = 2000L,
                    updatedAt = 2000L,
                )
            )
        }
    }

    @Test
    fun cascadeDeleteRemovesIngredients() = runTest {
        val prodIng = productDao.insert(TestData.product(barcode = null, name = "Ingrediente"))
        val prodRecipe = productDao.insert(TestData.product(barcode = null, name = "Receta", source = ProductSource.RECIPE))

        val recipeId = recipeDao.insertRecipe(
            RecipeEntity(
                productId = prodRecipe,
                name = "Receta",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )
        recipeDao.insertIngredients(
            listOf(
                RecipeIngredientEntity(recipeId = recipeId, position = 0, productId = prodIng, grams = 150.0),
            )
        )

        assertEquals(1, recipeDao.countRecipesUsingProduct(prodIng))

        recipeDao.deleteRecipeById(recipeId)

        val retrieved = recipeDao.getWithIngredientsById(recipeId)
        assertNull(retrieved)
        assertEquals(0, recipeDao.countRecipesUsingProduct(prodIng))
    }

    @Test
    fun getRecipeIdsUsingProductReturnsCorrectRecipe() = runTest {
        val prodA = productDao.insert(TestData.product(barcode = null, name = "Prod A"))
        val prodRecipe1 = productDao.insert(TestData.product(barcode = null, name = "R1", source = ProductSource.RECIPE))
        val prodRecipe2 = productDao.insert(TestData.product(barcode = null, name = "R2", source = ProductSource.RECIPE))

        val r1 = recipeDao.insertRecipe(RecipeEntity(productId = prodRecipe1, name = "R1", createdAt = 1, updatedAt = 1))
        val r2 = recipeDao.insertRecipe(RecipeEntity(productId = prodRecipe2, name = "R2", createdAt = 2, updatedAt = 2))

        recipeDao.insertIngredients(listOf(RecipeIngredientEntity(recipeId = r1, position = 0, productId = prodA, grams = 50.0)))
        recipeDao.insertIngredients(listOf(RecipeIngredientEntity(recipeId = r2, position = 0, productId = prodA, grams = 80.0)))

        val recipeIds = recipeDao.getRecipeIdsUsingProduct(prodA)
        assertEquals(2, recipeIds.size)
        assertTrue(recipeIds.contains(r1))
        assertTrue(recipeIds.contains(r2))
    }
}
