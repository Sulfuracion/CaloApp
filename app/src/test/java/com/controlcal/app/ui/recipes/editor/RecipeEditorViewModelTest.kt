package com.controlcal.app.ui.recipes.editor

import com.controlcal.app.test.FakeProductRepository
import com.controlcal.app.test.FakeRecipeRepository
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
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
class RecipeEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRecipeRepository: FakeRecipeRepository
    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var viewModel: RecipeEditorViewModel

    private val prodAvena = Product(
        id = 1L,
        name = "Avena suave",
        kcal100g = 370.0,
        protein100g = 13.5,
        carbs100g = 60.0,
        fat100g = 7.0,
    )

    private val prodLeche = Product(
        id = 2L,
        name = "Leche entera",
        kcal100g = 65.0,
        protein100g = 3.2,
        carbs100g = 4.8,
        fat100g = 3.8,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProductRepository = FakeProductRepository().apply {
            products.add(prodAvena)
            products.add(prodLeche)
        }
        fakeRecipeRepository = FakeRecipeRepository(
            productProvider = { fakeProductRepository.products.find { p -> p.id == it } },
        )

        viewModel = RecipeEditorViewModel(
            recipeRepository = fakeRecipeRepository,
            productRepository = fakeProductRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialize_newRecipe_setsEmptyState() = runTest {
        viewModel.initialize(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.recipeId)
        assertEquals("", state.name)
        assertTrue(state.ingredients.isEmpty())
        assertFalse(state.isValid)
    }

    @Test
    fun initialize_existingRecipe_loadsDataAndCalculates() = runTest {
        val saved = fakeRecipeRepository.saveRecipe(
            recipe = Recipe(
                id = 1L,
                productId = 101L,
                name = "Porridge",
                cookedWeightG = 250.0,
                servings = 1.0,
                notes = "Hervir 3 minutos",
            ),
            ingredients = listOf(
                RecipeIngredient(id = 1L, recipeId = 1L, position = 0, productId = prodAvena.id, grams = 50.0),
                RecipeIngredient(id = 2L, recipeId = 1L, position = 1, productId = prodLeche.id, grams = 200.0),
            ),
        )

        viewModel.initialize(saved.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1L, state.recipeId)
        assertEquals("Porridge", state.name)
        assertEquals(2, state.ingredients.size)
        assertEquals("250", state.cookedWeightGText)
        assertEquals("1", state.servingsText)
        assertEquals("Hervir 3 minutos", state.notes)
        assertTrue(state.isValid)
        assertNotNull(state.calculation)
    }

    @Test
    fun addingAndReorderingIngredients_updatesStateAndCalculation() = runTest {
        viewModel.initialize(null)
        advanceUntilIdle()

        viewModel.onNameChanged("Gachas")
        viewModel.onOpenAddIngredientDialog()
        assertTrue(viewModel.uiState.value.showAddIngredientDialog)

        // Select Avena
        viewModel.onSelectCandidateProduct(prodAvena)
        advanceUntilIdle()
        viewModel.onCandidateGramsChanged("50")
        viewModel.onConfirmAddIngredient()

        assertEquals(1, viewModel.uiState.value.ingredients.size)
        val firstTempId = viewModel.uiState.value.ingredients[0].tempId

        // Select Leche
        viewModel.onOpenAddIngredientDialog()
        viewModel.onSelectCandidateProduct(prodLeche)
        advanceUntilIdle()
        viewModel.onCandidateGramsChanged("200")
        viewModel.onConfirmAddIngredient()

        assertEquals(2, viewModel.uiState.value.ingredients.size)
        val secondTempId = viewModel.uiState.value.ingredients[1].tempId

        // Move Leche up
        viewModel.onMoveIngredientUp(secondTempId)
        assertEquals(secondTempId, viewModel.uiState.value.ingredients[0].tempId)
        assertEquals(firstTempId, viewModel.uiState.value.ingredients[1].tempId)

        // Remove Avena
        viewModel.onRemoveIngredient(firstTempId)
        assertEquals(1, viewModel.uiState.value.ingredients.size)
        assertEquals(prodLeche.id, viewModel.uiState.value.ingredients[0].product.id)
    }

    @Test
    fun cycleDetection_blocksCircularDependency() = runTest {
        // Recipe 1: Porridge (Product ID 101)
        val porridge = fakeRecipeRepository.saveRecipe(
            recipe = Recipe(id = 1L, productId = 101L, name = "Porridge"),
            ingredients = listOf(
                RecipeIngredient(productId = prodAvena.id, position = 0, grams = 50.0),
            ),
        )
        val porridgeProduct = fakeRecipeRepository.generatedProducts[101L]!!
        fakeProductRepository.products.add(porridgeProduct)

        // We are editing Recipe 1 and trying to add Porridge product into Recipe 1
        viewModel.initialize(porridge.id)
        advanceUntilIdle()

        viewModel.onOpenAddIngredientDialog()
        viewModel.onSelectCandidateProduct(porridgeProduct)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.cycleError)
        assertNull(state.selectedCandidateProduct)
    }

    @Test
    fun saveRecipe_persistsToRepositoryAndEmitsFeedback() = runTest {
        viewModel.initialize(null)
        advanceUntilIdle()

        viewModel.onNameChanged("Batido de avena")
        viewModel.onOpenAddIngredientDialog()
        viewModel.onSelectCandidateProduct(prodAvena)
        advanceUntilIdle()
        viewModel.onCandidateGramsChanged("40")
        viewModel.onConfirmAddIngredient()

        val events = mutableListOf<UiFeedbackEvent>()
        val job = launch {
            viewModel.feedbackEvents.collect { events.add(it) }
        }

        var savedCallbackCalled = false
        viewModel.saveRecipe { saved ->
            savedCallbackCalled = true
            assertEquals("Batido de avena", saved.name)
        }
        advanceUntilIdle()

        assertTrue(savedCallbackCalled)
        assertEquals(1, fakeRecipeRepository.recipes.size)
        assertEquals("Batido de avena", fakeRecipeRepository.recipes[0].name)
        assertTrue(events.any { it is UiFeedbackEvent.Success })

        job.cancel()
    }
}
