package com.controlcal.app.ui.savedmeals

import com.controlcal.app.test.FakeSavedMealRepository
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.core.model.Product
import com.controlcal.core.model.SavedMeal
import com.controlcal.core.model.SavedMealItem
import com.controlcal.core.repo.ProductRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedMealsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSavedMealRepository: FakeSavedMealRepository
    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var viewModel: SavedMealsViewModel

    private val sampleProduct1 = Product(
        id = 1L,
        name = "Avena copos",
        kcal100g = 370.0,
        protein100g = 13.5,
        carbs100g = 60.0,
        fat100g = 7.0,
    )

    private val sampleProduct2 = Product(
        id = 2L,
        name = "Leche entera",
        kcal100g = 65.0,
        protein100g = 3.2,
        carbs100g = 4.8,
        fat100g = 3.8,
        isLiquid = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSavedMealRepository = FakeSavedMealRepository()
        fakeProductRepository = FakeProductRepository()

        fakeProductRepository.products.add(sampleProduct1)
        fakeProductRepository.products.add(sampleProduct2)

        viewModel = SavedMealsViewModel(
            savedMealRepository = fakeSavedMealRepository,
            productRepository = fakeProductRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadMeals_resolvesAndRanksMeals() = runTest {
        val meal1 = SavedMeal(
            id = 1L,
            name = "Desayuno",
            createdAt = 1000L,
            updatedAt = 1000L,
            lastUsedAt = 1000L,
            useCount = 1,
            items = listOf(
                SavedMealItem(1, 1, 0, sampleProduct1.id, sampleProduct1.name, 100.0, null, null, null, null)
            ),
        )
        val meal2 = SavedMeal(
            id = 2L,
            name = "Merienda",
            createdAt = 2000L,
            updatedAt = 2000L,
            lastUsedAt = 5000L,
            useCount = 2,
            items = listOf(
                SavedMealItem(2, 2, 0, sampleProduct2.id, sampleProduct2.name, 200.0, null, null, null, null)
            ),
        )
        fakeSavedMealRepository.saveMeal(meal1)
        fakeSavedMealRepository.saveMeal(meal2)

        viewModel.loadMeals()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.meals.size)
        // meal2 was used more recently
        assertEquals(2L, state.meals[0].meal.id)
        assertEquals(1L, state.meals[1].meal.id)
        // Check totals calculation
        assertEquals(130.0, state.meals[0].totalKcal, 0.01) // 200g * 65kcal / 100g
        assertEquals(370.0, state.meals[1].totalKcal, 0.01) // 100g * 370kcal / 100g
    }

    @Test
    fun renameMeal_updatesMealNameAndRefreshes() = runTest {
        val meal = SavedMeal(
            id = 1L,
            name = "Nombre viejo",
            createdAt = 1000L,
            updatedAt = 1000L,
            lastUsedAt = null,
            useCount = 0,
            items = listOf(
                SavedMealItem(1, 1, 0, sampleProduct1.id, sampleProduct1.name, 100.0, null, null, null, null)
            ),
        )
        fakeSavedMealRepository.saveMeal(meal)

        viewModel.loadMeals()
        advanceUntilIdle()

        val resolved = viewModel.uiState.value.meals[0]
        viewModel.onOpenRenameDialog(resolved)
        assertEquals("Nombre viejo", viewModel.uiState.value.renameText)

        viewModel.onRenameTextChanged("Nombre nuevo")
        viewModel.onConfirmRename()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.renamingMeal)
        assertEquals("Nombre nuevo", fakeSavedMealRepository.savedMeals[0].name)
    }

    @Test
    fun renameMeal_withDuplicateName_setsError() = runTest {
        val meal1 = SavedMeal(1L, "Desayuno", 1000L, 1000L, null, 0, listOf(
            SavedMealItem(1, 1, 0, sampleProduct1.id, sampleProduct1.name, 100.0, null, null, null, null)
        ))
        val meal2 = SavedMeal(2L, "Merienda", 2000L, 2000L, null, 0, listOf(
            SavedMealItem(2, 2, 0, sampleProduct2.id, sampleProduct2.name, 100.0, null, null, null, null)
        ))
        fakeSavedMealRepository.saveMeal(meal1)
        fakeSavedMealRepository.saveMeal(meal2)

        viewModel.loadMeals()
        advanceUntilIdle()

        val resolved2 = viewModel.uiState.value.meals.find { it.meal.id == 2L }!!
        viewModel.onOpenRenameDialog(resolved2)
        viewModel.onRenameTextChanged("desayuno")
        viewModel.onConfirmRename()
        advanceUntilIdle()

        assertEquals("Ya existe una comida con este nombre", viewModel.uiState.value.renameError)
        assertNotNull(viewModel.uiState.value.renamingMeal)
    }

    @Test
    fun deleteMeal_removesFromRepository() = runTest {
        val meal = SavedMeal(1L, "Borrar", 1000L, 1000L, null, 0, listOf(
            SavedMealItem(1, 1, 0, sampleProduct1.id, sampleProduct1.name, 100.0, null, null, null, null)
        ))
        fakeSavedMealRepository.saveMeal(meal)

        viewModel.loadMeals()
        advanceUntilIdle()

        val resolved = viewModel.uiState.value.meals[0]
        viewModel.onOpenDeleteDialog(resolved)
        assertNotNull(viewModel.uiState.value.deletingMeal)

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deletingMeal)
        assertEquals(0, fakeSavedMealRepository.savedMeals.size)
        assertEquals(0, viewModel.uiState.value.meals.size)
    }

    @Test
    fun editItemGrams_updatesGramsAndTotals() = runTest {
        val meal = SavedMeal(1L, "Avena", 1000L, 1000L, null, 0, listOf(
            SavedMealItem(1, 1, 0, sampleProduct1.id, sampleProduct1.name, 50.0, null, null, null, null)
        ))
        fakeSavedMealRepository.saveMeal(meal)

        viewModel.loadMeals()
        advanceUntilIdle()

        val resolved = viewModel.uiState.value.meals[0]
        val item = resolved.meal.items[0]

        viewModel.onOpenEditGramsDialog(resolved.meal.id, item)
        assertEquals("50", viewModel.uiState.value.editGramsText)

        viewModel.onEditGramsChanged("150")
        viewModel.onConfirmEditGrams()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingMealItem)
        val updated = fakeSavedMealRepository.savedMeals[0]
        assertEquals(150.0, updated.items[0].grams, 0.001)
    }

    @Test
    fun removeItem_removesItemAndReindexesPositions() = runTest {
        val meal = SavedMeal(1L, "Completo", 1000L, 1000L, null, 0, listOf(
            SavedMealItem(1, 1, 0, sampleProduct1.id, sampleProduct1.name, 50.0, null, null, null, null),
            SavedMealItem(2, 1, 1, sampleProduct2.id, sampleProduct2.name, 200.0, null, null, null, null),
        ))
        fakeSavedMealRepository.saveMeal(meal)

        viewModel.loadMeals()
        advanceUntilIdle()

        val resolved = viewModel.uiState.value.meals[0]
        val firstItem = resolved.meal.items[0]

        viewModel.onRemoveItem(resolved.meal.id, firstItem)
        advanceUntilIdle()

        val updated = fakeSavedMealRepository.savedMeals[0]
        assertEquals(1, updated.items.size)
        assertEquals(sampleProduct2.id, updated.items[0].productId)
        assertEquals(0, updated.items[0].position)
    }

    @Test
    fun removeItem_lastItem_showsErrorMessage() = runTest {
        val meal = SavedMeal(1L, "Solo un item", 1000L, 1000L, null, 0, listOf(
            SavedMealItem(1, 1, 0, sampleProduct1.id, sampleProduct1.name, 50.0, null, null, null, null)
        ))
        fakeSavedMealRepository.saveMeal(meal)

        viewModel.loadMeals()
        advanceUntilIdle()

        val events = mutableListOf<UiFeedbackEvent>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.feedbackEvents.collect { events.add(it) }
        }

        val resolved = viewModel.uiState.value.meals[0]
        val onlyItem = resolved.meal.items[0]

        viewModel.onRemoveItem(resolved.meal.id, onlyItem)
        advanceUntilIdle()

        // Still has the item
        assertEquals(1, fakeSavedMealRepository.savedMeals[0].items.size)
        assertTrue(events.any { it is UiFeedbackEvent.Error })
    }

    @Test
    fun addProduct_appendsItemToMeal() = runTest {
        val meal = SavedMeal(1L, "Solo avena", 1000L, 1000L, null, 0, listOf(
            SavedMealItem(1, 1, 0, sampleProduct1.id, sampleProduct1.name, 50.0, null, null, null, null)
        ))
        fakeSavedMealRepository.saveMeal(meal)

        viewModel.loadMeals()
        advanceUntilIdle()

        val resolved = viewModel.uiState.value.meals[0]
        viewModel.onOpenAddProductDialog(resolved.meal.id)
        assertEquals(resolved.meal.id, viewModel.uiState.value.addingItemToMealId)

        viewModel.onSelectProductForAdd(sampleProduct2)
        viewModel.onAddGramsChanged("200")
        viewModel.onConfirmAddProduct()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.addingItemToMealId)
        val updated = fakeSavedMealRepository.savedMeals[0]
        assertEquals(2, updated.items.size)
        assertEquals(sampleProduct2.id, updated.items[1].productId)
        assertEquals(200.0, updated.items[1].grams, 0.001)
        assertEquals(1, updated.items[1].position)
    }
}

private class FakeProductRepository : ProductRepository {
    val products = mutableListOf<Product>()
    override suspend fun getById(id: Long): Product? = products.find { it.id == id }
    override suspend fun getByBarcode(barcode: String): Product? = products.find { it.barcode == barcode }
    override suspend fun searchByName(prefix: String): List<Product> = products.filter { it.name.startsWith(prefix, ignoreCase = true) }
    override suspend fun getActiveProducts(): List<Product> = products.filter { !it.archived }
    override suspend fun getAllIncludingArchived(): List<Product> = products.toList()
    override suspend fun getPantryProducts(): List<Product> = products.filter { !it.archived && it.inPantry }
    override suspend fun setInPantry(id: Long, inPantry: Boolean) {}
    override suspend fun updatePantryConfig(id: Long, inPantry: Boolean, minG: Double?, maxG: Double?, unitName: String?, unitG: Double?) {}
    override suspend fun insert(product: Product): Long {
        val id = if (product.id == 0L) (products.size + 1).toLong() else product.id
        products.add(product.copy(id = id))
        return id
    }
    override suspend fun update(product: Product) {}
    override suspend fun archive(id: Long) {}
    override suspend fun hasEntries(productId: Long): Boolean = false
}
