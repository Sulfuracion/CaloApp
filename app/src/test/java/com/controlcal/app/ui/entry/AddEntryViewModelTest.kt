package com.controlcal.app.ui.entry

import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.DefaultMeals
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.repo.MealConfigRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AddEntryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeMealEntryRepository: FakeMealEntryRepository
    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var fakeMealConfigRepository: FakeMealConfigRepository
    private lateinit var viewModel: AddEntryViewModel

    private val testClock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("UTC"))
    private val testDate = LocalDate.of(2026, 10, 15)

    private val sampleProduct = Product(
        id = 1L,
        name = "Avena copos",
        kcal100g = 370.0,
        protein100g = 13.5,
        carbs100g = 60.0,
        fat100g = 7.0,
        servingG = 50.0,
    )

    private val sampleProductWithUnit = Product(
        id = 2L,
        name = "Huevo",
        kcal100g = 150.0,
        protein100g = 12.5,
        carbs100g = 0.5,
        fat100g = 10.0,
        unitName = "huevo",
        unitG = 55.0,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeMealEntryRepository = FakeMealEntryRepository()
        fakeProductRepository = FakeProductRepository()
        fakeMealConfigRepository = FakeMealConfigRepository()

        fakeProductRepository.products.add(sampleProduct)
        fakeProductRepository.products.add(sampleProductWithUnit)

        viewModel = AddEntryViewModel(
            productRepository = fakeProductRepository,
            mealEntryRepository = fakeMealEntryRepository,
            mealConfigRepository = fakeMealConfigRepository,
            clock = testClock,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialize_setsContextAndLoadsMealNameAndHabituals() = runTest {
        // Insert historical entry
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-10",
                meal = DefaultMealIds.BREAKFAST,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 50.0,
                kcal = 185.0,
                protein = 6.75,
                carbs = 30.0,
                fat = 3.5,
                loggedAt = testClock.millis() - 100000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(testDate, state.targetDate)
        assertEquals(DefaultMealIds.BREAKFAST, state.targetMealId)
        assertEquals("Desayuno", state.targetMealName)
        assertEquals(1, state.habitualFoods.size)
        assertEquals("Avena copos", state.habitualFoods[0].product.name)
        assertEquals(50.0, state.habitualFoods[0].defaultGrams, 0.001)
    }

    @Test
    fun quickAddHabitual_logsImmediatelyWithPreselectedDateAndMealIn2Touches() = runTest {
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-10",
                meal = DefaultMealIds.BREAKFAST,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 50.0,
                kcal = 185.0,
                protein = 6.75,
                carbs = 30.0,
                fat = 3.5,
                loggedAt = testClock.millis() - 100000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        val habitual = viewModel.uiState.value.habitualFoods.first()
        var addedCallbackCalled = false
        viewModel.onQuickAddHabitual(habitual) {
            addedCallbackCalled = true
        }
        advanceUntilIdle()

        assertTrue(addedCallbackCalled)
        assertTrue(viewModel.uiState.value.isAddedSuccessfully)

        val inserted = fakeMealEntryRepository.entries.find { it.date == "2026-10-15" }
        assertNotNull(inserted)
        assertEquals(DefaultMealIds.BREAKFAST, inserted!!.meal)
        assertEquals("Avena copos", inserted.productName)
        assertEquals(50.0, inserted.grams, 0.001)
        assertEquals(185.0, inserted.kcal, 0.001)
    }

    @Test
    fun searchProducts_filtersByPrefix() = runTest {
        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Hue")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.searchResults.size)
        assertEquals("Huevo", state.searchResults[0].name)
    }

    @Test
    fun confirmAddProduct_inUnits_calculatesGramsAndMacros() = runTest {
        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        // Select egg (unitG = 55)
        viewModel.onSelectProductForPortion(sampleProductWithUnit)
        advanceUntilIdle()

        // Change units to 2
        viewModel.onQuantityInputChanged("2")
        advanceUntilIdle()

        var success = false
        viewModel.onConfirmAddProduct {
            success = true
        }
        advanceUntilIdle()

        assertTrue(success)
        val inserted = fakeMealEntryRepository.entries.find { it.date == "2026-10-15" && it.productId == 2L }
        assertNotNull(inserted)
        // 2 eggs * 55 g = 110 g
        assertEquals(110.0, inserted!!.grams, 0.001)
        // 110 g * 150 kcal/100g = 165 kcal
        assertEquals(165.0, inserted.kcal, 0.001)
    }
}

private class FakeProductRepository : ProductRepository {
    val products = mutableListOf<Product>()
    override suspend fun getById(id: Long): Product? = products.find { it.id == id }
    override suspend fun getByBarcode(barcode: String): Product? = products.find { it.barcode == barcode }
    override suspend fun searchByName(prefix: String): List<Product> = products.filter { it.name.startsWith(prefix, ignoreCase = true) }
    override suspend fun getActiveProducts(): List<Product> = products.filter { !it.archived }
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

private class FakeMealEntryRepository : MealEntryRepository {
    val entries = mutableListOf<MealEntry>()

    override suspend fun insert(entry: MealEntry): Long {
        val id = if (entry.id == 0L) (entries.size + 1).toLong() else entry.id
        entries.add(entry.copy(id = id))
        return id
    }

    override suspend fun insertAll(entries: List<MealEntry>): List<Long> {
        return entries.map { insert(it) }
    }

    override suspend fun update(entry: MealEntry) {
        val idx = entries.indexOfFirst { it.id == entry.id }
        if (idx >= 0) entries[idx] = entry
    }

    override suspend fun delete(entry: MealEntry) {
        entries.removeAll { it.id == entry.id }
    }

    override suspend fun getByDate(date: String): List<MealEntry> = entries.filter { it.date == date }
    override suspend fun getByDateAndMeal(date: String, meal: String): List<MealEntry> = entries.filter { it.date == date && it.meal == meal }
    override suspend fun getByProduct(productId: Long): List<MealEntry> = entries.filter { it.productId == productId }
    override suspend fun getSince(sinceMillis: Long): List<MealEntry> = entries.filter { it.loggedAt >= sinceMillis }
    override suspend fun getLatestDateWithMealBefore(date: String, meal: String): String? {
        return entries.filter { it.date < date && it.meal == meal }
            .maxByOrNull { it.date }?.date
    }
    override suspend fun countByMeal(meal: String): Int = entries.count { it.meal == meal }
}

private class FakeMealConfigRepository : MealConfigRepository {
    private var configs = DefaultMeals.defaults().toMutableList()
    override suspend fun getMealConfigs(): List<ConfiguredMeal> = configs
    override suspend fun saveMealConfigs(configs: List<ConfiguredMeal>) {
        this.configs = configs.toMutableList()
    }
}
