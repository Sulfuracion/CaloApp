package com.controlcal.app.ui.pantry

import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.repo.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PantryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var viewModel: PantryViewModel

    private val egg = Product(
        id = 1L,
        name = "Huevos camperos",
        brand = "Granja Real",
        kcal100g = 143.0,
        protein100g = 13.0,
        carbs100g = 0.7,
        fat100g = 9.5,
        inPantry = true,
        unitName = "huevo",
        unitG = 55.0,
        minG = 55.0,
        maxG = 165.0,
        source = ProductSource.MANUAL,
    )

    private val oats = Product(
        id = 2L,
        name = "Copos de avena",
        brand = "Bio",
        kcal100g = 370.0,
        protein100g = 13.5,
        carbs100g = 58.7,
        fat100g = 7.0,
        inPantry = false,
        source = ProductSource.MANUAL,
    )

    private val oliveOil = Product(
        id = 3L,
        name = "Aceite de oliva virgen extra",
        brand = "Olivares",
        kcal100g = 884.0,
        protein100g = 0.0,
        carbs100g = 0.0,
        fat100g = 100.0,
        inPantry = true,
        source = ProductSource.MANUAL,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProductRepository = FakeProductRepository().apply {
            products.addAll(listOf(egg, oats, oliveOil))
        }
        viewModel = PantryViewModel(fakeProductRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadProducts_populatesPantryByDefault() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(3, state.allProducts.size)
        assertEquals(2, state.pantryProducts.size)
        assertTrue(state.showOnlyPantry)
        assertEquals(2, state.displayedProducts.size)
        assertEquals(listOf("Huevos camperos", "Aceite de oliva virgen extra"), state.displayedProducts.map { it.name })
    }

    @Test
    fun onFilterToggled_showsAllProducts() = runTest {
        advanceUntilIdle()

        viewModel.onFilterToggled(false)
        val state = viewModel.uiState.value

        assertFalse(state.showOnlyPantry)
        assertEquals(3, state.displayedProducts.size)
    }

    @Test
    fun onSearchQueryChanged_filtersByNameAndBrand() = runTest {
        advanceUntilIdle()

        // Filter in pantry with query
        viewModel.onSearchQueryChanged("huevo")
        assertEquals(1, viewModel.uiState.value.displayedProducts.size)
        assertEquals("Huevos camperos", viewModel.uiState.value.displayedProducts.first().name)

        // Switch to all and filter by brand
        viewModel.onFilterToggled(false)
        viewModel.onSearchQueryChanged("bio")
        assertEquals(1, viewModel.uiState.value.displayedProducts.size)
        assertEquals("Copos de avena", viewModel.uiState.value.displayedProducts.first().name)
    }

    @Test
    fun onTogglePantry_updatesProductAndReloads() = runTest {
        advanceUntilIdle()

        viewModel.onTogglePantry(oats)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.pantryProducts.size)
        assertNotNull(state.snackbarMessage)
        assertTrue(state.snackbarMessage!!.contains("añadido a la despensa"))

        val inRepo = fakeProductRepository.getById(oats.id)
        assertTrue(inRepo!!.inPantry)
    }

    @Test
    fun onStartEditingProduct_populatesEditFields() = runTest {
        advanceUntilIdle()

        viewModel.onStartEditingProduct(egg)
        val state = viewModel.uiState.value

        assertNotNull(state.editingProduct)
        assertEquals("Huevos camperos", state.editingProduct!!.name)
        assertTrue(state.editInPantry)
        assertEquals("55", state.editMinGText)
        assertEquals("165", state.editMaxGText)
        assertEquals("huevo", state.editUnitNameText)
        assertEquals("55", state.editUnitGText)
    }

    @Test
    fun onSaveProductConfig_withInvalidLimits_displaysError() = runTest {
        advanceUntilIdle()

        viewModel.onStartEditingProduct(egg)
        viewModel.onEditMinGChanged("200")
        viewModel.onEditMaxGChanged("100") // min > max

        viewModel.onSaveProductConfig()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.editingProduct) // not dismissed
        assertNotNull(state.editMinGError)
        assertNotNull(state.editMaxGError)
    }

    @Test
    fun onSaveProductConfig_withUnitNameWithoutUnitGrams_displaysError() = runTest {
        advanceUntilIdle()

        viewModel.onStartEditingProduct(oats)
        viewModel.onEditUnitNameChanged("vaso")
        viewModel.onEditUnitGChanged("") // empty unitG

        viewModel.onSaveProductConfig()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.editingProduct)
        assertNotNull(state.editUnitGError)
    }

    @Test
    fun onSaveProductConfig_withValidData_persistsAndCloses() = runTest {
        advanceUntilIdle()

        viewModel.onStartEditingProduct(oats)
        viewModel.onEditInPantryChanged(true)
        viewModel.onEditMinGChanged("40")
        viewModel.onEditMaxGChanged("100")
        viewModel.onEditUnitNameChanged("cucharada")
        viewModel.onEditUnitGChanged("15")

        viewModel.onSaveProductConfig()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.editingProduct)
        assertNotNull(state.snackbarMessage)

        val updated = fakeProductRepository.getById(oats.id)!!
        assertTrue(updated.inPantry)
        assertEquals(40.0, updated.minG!!, 0.001)
        assertEquals(100.0, updated.maxG!!, 0.001)
        assertEquals("cucharada", updated.unitName)
        assertEquals(15.0, updated.unitG!!, 0.001)
    }
}

private class FakeProductRepository : ProductRepository {
    val products = mutableListOf<Product>()

    override suspend fun getById(id: Long): Product? = products.find { it.id == id }
    override suspend fun getByBarcode(barcode: String): Product? = products.find { it.barcode == barcode }
    override suspend fun searchByName(prefix: String): List<Product> = products.filter { it.name.startsWith(prefix, ignoreCase = true) }
    override suspend fun getActiveProducts(): List<Product> = products.filter { !it.archived }
    override suspend fun getPantryProducts(): List<Product> = products.filter { !it.archived && it.inPantry }
    override suspend fun setInPantry(id: Long, inPantry: Boolean) {
        val idx = products.indexOfFirst { it.id == id }
        if (idx >= 0) products[idx] = products[idx].copy(inPantry = inPantry)
    }
    override suspend fun updatePantryConfig(
        id: Long,
        inPantry: Boolean,
        minG: Double?,
        maxG: Double?,
        unitName: String?,
        unitG: Double?,
    ) {
        val idx = products.indexOfFirst { it.id == id }
        if (idx >= 0) {
            products[idx] = products[idx].copy(
                inPantry = inPantry,
                minG = minG,
                maxG = maxG,
                unitName = unitName,
                unitG = unitG,
            )
        }
    }
    override suspend fun insert(product: Product): Long {
        val id = if (product.id == 0L) (products.size + 1).toLong() else product.id
        products.add(product.copy(id = id))
        return id
    }
    override suspend fun update(product: Product) {
        val idx = products.indexOfFirst { it.id == product.id }
        if (idx >= 0) products[idx] = product
    }
    override suspend fun archive(id: Long) {
        val idx = products.indexOfFirst { it.id == id }
        if (idx >= 0) products[idx] = products[idx].copy(archived = true)
    }
    override suspend fun hasEntries(productId: Long): Boolean = false
}
