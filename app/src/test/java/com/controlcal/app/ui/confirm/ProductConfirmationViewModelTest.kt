package com.controlcal.app.ui.confirm

import com.controlcal.app.data.off.OffProduct
import com.controlcal.core.model.Meal
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.repo.MealEntryRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProductConfirmationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var fakeMealEntryRepository: FakeMealEntryRepository
    private lateinit var viewModel: ProductConfirmationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProductRepository = FakeProductRepository()
        fakeMealEntryRepository = FakeMealEntryRepository()
        viewModel = ProductConfirmationViewModel(
            productRepository = fakeProductRepository,
            mealEntryRepository = fakeMealEntryRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialize_completeProduct_populatesAllFieldsWithoutMissingFlags() {
        val complete = OffProduct(
            barcode = "8412345678901",
            name = "Avena Copos",
            brand = "Marca Eco",
            kcal100g = 370.0,
            protein100g = 13.5,
            carbs100g = 60.0,
            fat100g = 7.0,
            fiber100g = 10.0,
            sugars100g = 1.0,
            satFat100g = 1.2,
            salt100g = 0.02,
            servingG = 40.0,
            isLiquid = false
        )

        viewModel.initialize(complete)
        val state = viewModel.uiState.value

        assertEquals("8412345678901", state.barcode)
        assertEquals("Avena Copos", state.name)
        assertEquals("Marca Eco", state.brand)
        assertEquals("370", state.kcal100g)
        assertEquals("13,5", state.protein100g)
        assertEquals("60", state.carbs100g)
        assertEquals("7", state.fat100g)
        assertEquals("40", state.servingG)
        assertEquals("40", state.diaryGrams)
        assertFalse(state.hasOriginallyMissingFields)
        assertTrue(state.isSaveEnabled)
    }

    @Test
    fun initialize_missingMandatoryMacros_flagsThemAsMissing() {
        val missingMacros = OffProduct(
            barcode = "8412345678902",
            name = "Yogur",
            brand = "Marca",
            kcal100g = null, // missing kcal
            protein100g = 4.0,
            carbs100g = null, // missing carbs
            fat100g = 2.5
        )

        viewModel.initialize(missingMacros)
        val state = viewModel.uiState.value

        assertTrue(state.originallyMissingKcal)
        assertTrue(state.originallyMissingCarbs)
        assertFalse(state.originallyMissingProtein)
        assertFalse(state.originallyMissingFat)
        assertTrue(state.hasOriginallyMissingFields)
        assertEquals("", state.kcal100g)
        assertEquals("", state.carbs100g)
        assertFalse(state.isSaveEnabled)
    }

    @Test
    fun saveProduct_requiresAllMandatoryFields() = runTest {
        val missingKcal = OffProduct(
            barcode = "8412345678903",
            name = "Manzana",
            brand = null,
            kcal100g = null,
            protein100g = 0.3,
            carbs100g = 14.0,
            fat100g = 0.2
        )
        viewModel.initialize(missingKcal)

        var savedCalled = false
        viewModel.onConfirmAndSave { savedCalled = true }
        advanceUntilIdle()

        assertFalse(savedCalled)
        assertEquals(0, fakeProductRepository.products.size)
        assertNotNull(viewModel.uiState.value.kcalError)

        // Fill in kcal and retry
        viewModel.onKcalChanged("52")
        viewModel.onConfirmAndSave { savedCalled = true }
        advanceUntilIdle()

        assertTrue(savedCalled)
        assertEquals(1, fakeProductRepository.products.size)
        val saved = fakeProductRepository.products.first()
        assertEquals("Manzana", saved.name)
        assertEquals(52.0, saved.kcal100g, 0.001)
        assertEquals(ProductSource.OFF, saved.source)
    }

    @Test
    fun saveProduct_whenAddToDiaryEnabled_insertsMealEntry() = runTest {
        val product = OffProduct(
            barcode = "8412345678904",
            name = "Plátano",
            brand = null,
            kcal100g = 89.0,
            protein100g = 1.1,
            carbs100g = 22.8,
            fat100g = 0.3
        )
        viewModel.initialize(product)
        viewModel.onAddToDiaryChanged(true)
        viewModel.onMealSelected(Meal.SNACK)
        viewModel.onDiaryGramsChanged("150")

        var savedSuccess = false
        viewModel.onConfirmAndSave { savedSuccess = true }
        advanceUntilIdle()

        assertTrue(savedSuccess)
        assertEquals(1, fakeProductRepository.products.size)
        val savedProd = fakeProductRepository.products.first()
        assertEquals(ProductSource.OFF, savedProd.source)

        assertEquals(1, fakeMealEntryRepository.entries.size)
        val entry = fakeMealEntryRepository.entries.first()
        assertEquals(LocalDate.now().toString(), entry.date)
        assertEquals(Meal.SNACK, entry.meal)
        assertEquals(savedProd.id, entry.productId)
        assertEquals("Plátano", entry.productName)
        assertEquals(150.0, entry.grams, 0.001)
        assertEquals(133.5, entry.kcal, 0.001) // 89 * 1.5
    }

    @Test
    fun discard_doesNotPersistAnything() = runTest {
        val product = OffProduct(
            barcode = "8412345678905",
            name = "Leche",
            brand = null,
            kcal100g = 63.0,
            protein100g = 3.1,
            carbs100g = 4.8,
            fat100g = 3.6
        )
        viewModel.initialize(product)

        // User edits some fields but cancels without confirming
        viewModel.onNameChanged("Leche desnatada")
        viewModel.onKcalChanged("35")

        assertEquals(0, fakeProductRepository.products.size)
        assertEquals(0, fakeMealEntryRepository.entries.size)
    }
}

private class FakeProductRepository : ProductRepository {
    val products = mutableListOf<Product>()

    override suspend fun getById(id: Long): Product? = products.find { it.id == id }
    override suspend fun getByBarcode(barcode: String): Product? = products.find { it.barcode == barcode }
    override suspend fun searchByName(prefix: String): List<Product> = products.filter { it.name.startsWith(prefix, ignoreCase = true) }
    override suspend fun getActiveProducts(): List<Product> = products.filter { !it.archived }
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

private class FakeMealEntryRepository : MealEntryRepository {
    val entries = mutableListOf<MealEntry>()

    override suspend fun insert(entry: MealEntry): Long {
        val id = if (entry.id == 0L) (entries.size + 1).toLong() else entry.id
        entries.add(entry.copy(id = id))
        return id
    }
    override suspend fun update(entry: MealEntry) {}
    override suspend fun delete(entry: MealEntry) {}
    override suspend fun getByDate(date: String): List<MealEntry> = entries.filter { it.date == date }
    override suspend fun getByDateAndMeal(date: String, meal: Meal): List<MealEntry> = entries.filter { it.date == date && it.meal == meal }
    override suspend fun getByProduct(productId: Long): List<MealEntry> = entries.filter { it.productId == productId }
}
