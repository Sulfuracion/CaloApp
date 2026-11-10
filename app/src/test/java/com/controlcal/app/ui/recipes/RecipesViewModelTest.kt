package com.controlcal.app.ui.recipes

import com.controlcal.app.test.FakeRecipeRepository
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.core.model.Product
import com.controlcal.core.model.Recipe
import com.controlcal.core.model.RecipeIngredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRecipeRepository: FakeRecipeRepository
    private lateinit var viewModel: RecipesViewModel

    private val sampleProduct1 = Product(
        id = 1L,
        name = "Arroz blanco",
        kcal100g = 360.0,
        protein100g = 7.0,
        carbs100g = 80.0,
        fat100g = 1.0,
    )

    private val sampleProduct2 = Product(
        id = 2L,
        name = "Pollo pechuga",
        kcal100g = 120.0,
        protein100g = 22.0,
        carbs100g = 0.0,
        fat100g = 2.5,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val productsMap = mapOf(
            sampleProduct1.id to sampleProduct1,
            sampleProduct2.id to sampleProduct2,
        )
        fakeRecipeRepository = FakeRecipeRepository(
            productProvider = { productsMap[it] },
        )

        viewModel = RecipesViewModel(
            recipeRepository = fakeRecipeRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadRecipes_loadsAndPopulatesState() = runTest {
        fakeRecipeRepository.saveRecipe(
            recipe = Recipe(id = 1L, productId = 101L, name = "Arroz con pollo"),
            ingredients = listOf(
                RecipeIngredient(id = 1L, recipeId = 1L, position = 0, productId = sampleProduct1.id, grams = 100.0),
                RecipeIngredient(id = 2L, recipeId = 1L, position = 1, productId = sampleProduct2.id, grams = 200.0),
            ),
        )

        viewModel.loadRecipes()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.recipes.size)
        assertEquals("Arroz con pollo", state.recipes[0].name)
        assertEquals(1, state.filteredRecipes.size)
    }

    @Test
    fun searchQuery_filtersRecipesCorrectly() = runTest {
        fakeRecipeRepository.saveRecipe(
            recipe = Recipe(id = 1L, productId = 101L, name = "Arroz con pollo"),
            ingredients = listOf(RecipeIngredient(productId = sampleProduct1.id, position = 0, grams = 100.0)),
        )
        fakeRecipeRepository.saveRecipe(
            recipe = Recipe(id = 2L, productId = 102L, name = "Tortilla de patatas"),
            ingredients = listOf(RecipeIngredient(productId = sampleProduct2.id, position = 0, grams = 100.0)),
        )

        viewModel.loadRecipes()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.filteredRecipes.size)

        viewModel.onSearchQueryChanged("pollo")
        assertEquals(1, viewModel.uiState.value.filteredRecipes.size)
        assertEquals("Arroz con pollo", viewModel.uiState.value.filteredRecipes[0].name)

        viewModel.onSearchQueryChanged("TORTILLA")
        assertEquals(1, viewModel.uiState.value.filteredRecipes.size)
        assertEquals("Tortilla de patatas", viewModel.uiState.value.filteredRecipes[0].name)

        viewModel.onSearchQueryChanged("inexistente")
        assertEquals(0, viewModel.uiState.value.filteredRecipes.size)
    }

    @Test
    fun deleteRecipe_whenNoEntries_deletesPermanently() = runTest {
        val saved = fakeRecipeRepository.saveRecipe(
            recipe = Recipe(id = 1L, productId = 101L, name = "Arroz con pollo"),
            ingredients = listOf(RecipeIngredient(productId = sampleProduct1.id, position = 0, grams = 100.0)),
        )

        viewModel.loadRecipes()
        advanceUntilIdle()

        val events = mutableListOf<UiFeedbackEvent>()
        val job = launch {
            viewModel.feedbackEvents.collect { events.add(it) }
        }

        viewModel.onShowDeleteDialog(saved)
        assertNotNull(viewModel.uiState.value.deleteDialogRecipe)

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteDialogRecipe)
        assertEquals(0, viewModel.uiState.value.recipes.size)
        assertTrue(events.any { it is UiFeedbackEvent.Success })

        job.cancel()
    }

    @Test
    fun deleteRecipe_whenHasMealEntries_archivesRecipe() = runTest {
        val saved = fakeRecipeRepository.saveRecipe(
            recipe = Recipe(id = 1L, productId = 101L, name = "Arroz con pollo"),
            ingredients = listOf(RecipeIngredient(productId = sampleProduct1.id, position = 0, grams = 100.0)),
        )
        fakeRecipeRepository.simulatedMealEntriesCount = { if (it == 101L) 3 else 0 }

        viewModel.loadRecipes()
        advanceUntilIdle()

        val events = mutableListOf<UiFeedbackEvent>()
        val job = launch {
            viewModel.feedbackEvents.collect { events.add(it) }
        }

        viewModel.onShowDeleteDialog(saved)
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        // Active list should be empty (since archived)
        assertEquals(0, viewModel.uiState.value.recipes.size)
        // Recipe entity in repo still exists but archived = true
        val inRepo = fakeRecipeRepository.recipes.find { it.id == 1L }
        assertNotNull(inRepo)
        assertTrue(inRepo!!.archived)
        assertTrue(events.any { it is UiFeedbackEvent.Success })

        job.cancel()
    }
}
