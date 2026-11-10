package com.controlcal.app.data.repo

import com.controlcal.app.data.db.DaoTestBase
import com.controlcal.app.data.db.TestData
import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.app.data.db.entity.MealEntryEntity
import com.controlcal.core.model.DayType
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.model.Recipe
import com.controlcal.core.model.RecipeIngredient
import com.controlcal.core.repo.RecipeDeleteResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class RecipeRepositoryTest : DaoTestBase() {

    private lateinit var recipeRepository: RecipeRepositoryImpl
    private lateinit var productRepository: ProductRepositoryImpl

    @Before
    fun initRepositories() {
        recipeRepository = RecipeRepositoryImpl(
            database = db,
            recipeDao = db.recipeDao(),
            productDao = db.productDao(),
            mealEntryDao = db.mealEntryDao(),
        )
        productRepository = ProductRepositoryImpl(
            productDao = db.productDao(),
            mealEntryDao = db.mealEntryDao(),
            recipeRepositoryProvider = { recipeRepository },
        )
    }

    @Test
    fun saveRecipeCreatesProductWithSourceRecipe() = runTest {
        val prod1 = productRepository.insert(
            Product(name = "Lentejas", kcal100g = 300.0, protein100g = 24.0, carbs100g = 50.0, fat100g = 1.0)
        )
        val prod2 = productRepository.insert(
            Product(name = "Zanahoria", kcal100g = 40.0, protein100g = 1.0, carbs100g = 8.0, fat100g = 0.2)
        )

        val recipe = Recipe(
            name = "Lentejas con zanahoria",
            cookedWeightG = 500.0,
            servings = 2.0,
            notes = "Cocinar a fuego lento",
        )
        val ingredients = listOf(
            RecipeIngredient(position = 0, productId = prod1, grams = 200.0), // 600 kcal
            RecipeIngredient(position = 1, productId = prod2, grams = 100.0), // 40 kcal
        ) // Total 640 kcal, cookedWeight = 500g -> 128 kcal/100g

        val saved = recipeRepository.saveRecipe(recipe, ingredients)

        assertTrue(saved.id > 0)
        assertTrue(saved.productId > 0)
        assertEquals("Lentejas con zanahoria", saved.name)
        assertEquals(2, saved.ingredients.size)

        val product = productRepository.getById(saved.productId)
        assertNotNull(product)
        assertEquals(ProductSource.RECIPE, product!!.source)
        assertEquals("Lentejas con zanahoria", product.name)
        assertEquals(128.0, product.kcal100g, 0.001)
        assertEquals("ración", product.unitName)
        assertEquals(250.0, product.unitG!!, 0.001)
        assertEquals(250.0, product.servingG!!, 0.001)
    }

    @Test
    fun updatingIngredientInPantryRecalculatesRecipeProduct() = runTest {
        val prod1 = productRepository.insert(
            Product(name = "Avena", kcal100g = 350.0, protein100g = 12.0, carbs100g = 60.0, fat100g = 7.0)
        )
        val recipe = Recipe(name = "Gachas", cookedWeightG = 200.0)
        val savedRecipe = recipeRepository.saveRecipe(
            recipe = recipe,
            ingredients = listOf(RecipeIngredient(position = 0, productId = prod1, grams = 100.0)),
        )

        val initialProduct = productRepository.getById(savedRecipe.productId)!!
        assertEquals(175.0, initialProduct.kcal100g, 0.001) // 350 / 2

        // Now user edits Avena in pantry: 400 kcal/100g instead of 350
        val updatedAvena = productRepository.getById(prod1)!!.copy(kcal100g = 400.0)
        productRepository.update(updatedAvena)

        // Recipe product should have been automatically recalculated!
        val recalculatedProduct = productRepository.getById(savedRecipe.productId)!!
        assertEquals(200.0, recalculatedProduct.kcal100g, 0.001) // 400 / 2
    }

    @Test
    fun deleteRecipePhysicallyIfNoDiaryEntries() = runTest {
        val prod1 = productRepository.insert(
            Product(name = "Ingrediente", kcal100g = 100.0, protein100g = 10.0, carbs100g = 10.0, fat100g = 2.0)
        )
        val saved = recipeRepository.saveRecipe(
            Recipe(name = "Para borrar"),
            listOf(RecipeIngredient(position = 0, productId = prod1, grams = 100.0)),
        )

        val result = recipeRepository.deleteRecipe(saved.id)
        assertEquals(RecipeDeleteResult.Deleted, result)

        assertNull(recipeRepository.getById(saved.id))
        assertNull(productRepository.getById(saved.productId))
    }

    @Test
    fun deleteRecipeArchivesIfProductHasDiaryEntries() = runTest {
        val prod1 = productRepository.insert(
            Product(name = "Ingrediente", kcal100g = 100.0, protein100g = 10.0, carbs100g = 10.0, fat100g = 2.0)
        )
        val saved = recipeRepository.saveRecipe(
            Recipe(name = "Receta consumida"),
            listOf(RecipeIngredient(position = 0, productId = prod1, grams = 100.0)),
        )

        // Log entry in diary for this recipe product
        db.dailyLogDao().insert(DailyLogEntity(date = "2026-11-10", dayType = DayType.REST))
        db.mealEntryDao().insert(
            MealEntryEntity(
                date = "2026-11-10",
                meal = "LUNCH",
                productId = saved.productId,
                productName = "Receta consumida",
                grams = 200.0,
                kcal = 300.0,
                protein = 20.0,
                carbs = 30.0,
                fat = 10.0,
                loggedAt = 1000L,
            )
        )

        val result = recipeRepository.deleteRecipe(saved.id)
        assertTrue(result is RecipeDeleteResult.Archived)
        assertTrue((result as RecipeDeleteResult.Archived).hasMealEntries)

        // Recipe and product should not be deleted, but archived
        val archivedRecipe = recipeRepository.getById(saved.id)
        assertNotNull(archivedRecipe)
        assertTrue(archivedRecipe!!.archived)

        val archivedProduct = productRepository.getById(saved.productId)
        assertNotNull(archivedProduct)
        assertTrue(archivedProduct!!.archived)
    }

    @Test
    fun cycleDetectionPreventsCircularRecipe() = runTest {
        val baseProd = productRepository.insert(
            Product(name = "Tomate", kcal100g = 20.0, protein100g = 1.0, carbs100g = 4.0, fat100g = 0.2)
        )

        // Create Recipe A (Salsa de tomate)
        val recipeA = recipeRepository.saveRecipe(
            Recipe(name = "Salsa de tomate"),
            listOf(RecipeIngredient(position = 0, productId = baseProd, grams = 100.0)),
        )

        // Create Recipe B (Lasaña) containing Recipe A
        val recipeB = recipeRepository.saveRecipe(
            Recipe(name = "Lasaña"),
            listOf(RecipeIngredient(position = 0, productId = recipeA.productId, grams = 150.0)),
        )

        // Now try to edit Recipe A to contain Recipe B -> CIRCULAR DEPENDENCY!
        assertFailsWith<IllegalArgumentException> {
            recipeRepository.saveRecipe(
                recipeA.recipe,
                listOf(
                    RecipeIngredient(position = 0, productId = baseProd, grams = 100.0),
                    RecipeIngredient(position = 1, productId = recipeB.productId, grams = 50.0),
                ),
            )
        }
    }
}
