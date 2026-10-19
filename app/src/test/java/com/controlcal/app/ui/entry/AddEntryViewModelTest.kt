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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.controlcal.app.data.off.OffClient
import com.controlcal.app.data.off.OffProduct
import com.controlcal.app.data.off.OffSearchByNameResult
import com.controlcal.app.data.off.OffSearchItem
import com.controlcal.app.data.off.OffSearchResult
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
    private lateinit var fakeOffClient: FakeOffClient
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
        fakeOffClient = FakeOffClient()

        fakeProductRepository.products.add(sampleProduct)
        fakeProductRepository.products.add(sampleProductWithUnit)

        viewModel = AddEntryViewModel(
            productRepository = fakeProductRepository,
            mealEntryRepository = fakeMealEntryRepository,
            mealConfigRepository = fakeMealConfigRepository,
            offClient = fakeOffClient,
            clock = testClock,
            defaultDispatcher = testDispatcher,
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
    fun searchProducts_filtersBySubstringAndBrand() = runTest {
        val pascualMilk = Product(
            id = 3L,
            barcode = "8410100012345",
            name = "Leche semidesnatada",
            brand = "Pascual",
            kcal100g = 46.0,
            protein100g = 3.1,
            carbs100g = 4.7,
            fat100g = 1.6,
        )
        fakeProductRepository.products.add(pascualMilk)

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        // Match substring in name
        viewModel.onSearchQueryChanged("semidesnatada")
        advanceUntilIdle()
        val stateName = viewModel.uiState.value
        assertEquals(1, stateName.searchResults.size)
        assertEquals("Leche semidesnatada", stateName.searchResults[0].name)

        // Match brand
        viewModel.onSearchQueryChanged("Pascual")
        advanceUntilIdle()
        val stateBrand = viewModel.uiState.value
        assertEquals(1, stateBrand.searchResults.size)
        assertEquals("Leche semidesnatada", stateBrand.searchResults[0].name)
    }

    @Test
    fun searchProducts_ignoresAccentsCaseAndEnye() = runTest {
        val banana = Product(
            id = 4L,
            name = "Plátano de Canarias",
            kcal100g = 89.0,
            protein100g = 1.1,
            carbs100g = 20.0,
            fat100g = 0.3,
        )
        val lasagne = Product(
            id = 5L,
            name = "Lasaña boloñesa",
            kcal100g = 135.0,
            protein100g = 7.0,
            carbs100g = 12.0,
            fat100g = 6.0,
        )
        fakeProductRepository.products.add(banana)
        fakeProductRepository.products.add(lasagne)

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        // Search without accent
        viewModel.onSearchQueryChanged("platano")
        advanceUntilIdle()
        val state1 = viewModel.uiState.value
        assertEquals(1, state1.searchResults.size)
        assertEquals("Plátano de Canarias", state1.searchResults[0].name)

        // Search without enye and in uppercase
        viewModel.onSearchQueryChanged("LASANA")
        advanceUntilIdle()
        val state2 = viewModel.uiState.value
        assertEquals(1, state2.searchResults.size)
        assertEquals("Lasaña boloñesa", state2.searchResults[0].name)
    }

    @Test
    fun searchProducts_matchesBarcodeWithEightOrMoreDigits() = runTest {
        val itemWithBarcode = Product(
            id = 7L,
            barcode = "8410100012345",
            name = "Queso fresco",
            kcal100g = 100.0,
            protein100g = 12.0,
            carbs100g = 3.0,
            fat100g = 4.0,
        )
        fakeProductRepository.products.add(itemWithBarcode)

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("8410100012345")
        advanceUntilIdle()
        val stateExact = viewModel.uiState.value
        assertEquals(1, stateExact.searchResults.size)
        assertEquals("Queso fresco", stateExact.searchResults[0].name)

        viewModel.onSearchQueryChanged("84101000")
        advanceUntilIdle()
        val statePrefix = viewModel.uiState.value
        assertEquals(1, statePrefix.searchResults.size)
        assertEquals("Queso fresco", statePrefix.searchResults[0].name)
    }

    @Test
    fun searchProducts_excludesArchivedProducts() = runTest {
        val archivedProduct = Product(
            id = 8L,
            name = "Huevo duro",
            kcal100g = 150.0,
            protein100g = 12.5,
            carbs100g = 0.5,
            fat100g = 10.0,
            archived = true,
        )
        fakeProductRepository.products.add(archivedProduct)

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("Huevo")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Only active "Huevo" should appear, not "Huevo duro"
        assertEquals(1, state.searchResults.size)
        assertEquals("Huevo", state.searchResults[0].name)
    }

    @Test
    fun searchProducts_ranksRecentProductsFirst() = runTest {
        // sampleProduct is Avena copos (id = 1), sampleProductWithUnit is Huevo (id = 2)
        // Log "Huevo" as recent
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 10L,
                date = "2026-10-14",
                meal = DefaultMealIds.LUNCH,
                productId = sampleProductWithUnit.id,
                productName = sampleProductWithUnit.name,
                grams = 110.0,
                kcal = 165.0,
                protein = 13.75,
                carbs = 0.55,
                fat = 11.0,
                loggedAt = testClock.millis() - 5000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        val results = viewModel.uiState.value.searchResults
        // "Huevo" is recent so it must appear first even though "Avena" is alphabetically earlier
        assertEquals(2, results.size)
        assertEquals("Huevo", results[0].name)
        assertEquals("Avena copos", results[1].name)
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

    @Test
    fun searchQueryChanged_doesNotTriggerOffSearch() = runTest {
        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("avena")
        advanceUntilIdle()

        assertEquals(0, fakeOffClient.searchCallCount)
        assertEquals(emptyList<OffSearchItem>(), viewModel.uiState.value.offSearchResults)
    }

    @Test
    fun onSearchInOff_whenQueryNotEmpty_triggersOffSearchAndUpdatesState() = runTest {
        val sampleOffProduct = OffProduct(
            barcode = "8412345678901",
            name = "Avena bio OFF",
            brand = "BioMarca",
            kcal100g = 360.0,
            protein100g = 12.0,
            carbs100g = 60.0,
            fat100g = 6.0,
        )
        val sampleOffItem = OffSearchItem(
            code = "8412345678901",
            name = "Avena bio OFF",
            brand = "BioMarca",
            kcal100g = 360.0,
            offProduct = sampleOffProduct,
        )
        fakeOffClient.searchResultToReturn = OffSearchByNameResult.Success(listOf(sampleOffItem))

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("avena bio")
        advanceUntilIdle()

        viewModel.onSearchInOff()
        advanceUntilIdle()

        assertEquals(1, fakeOffClient.searchCallCount)
        assertEquals("avena bio", fakeOffClient.lastSearchedQuery)
        val state = viewModel.uiState.value
        assertEquals(1, state.offSearchResults.size)
        assertEquals("Avena bio OFF", state.offSearchResults[0].name)
        assertNull(state.offSearchMessage)
    }

    @Test
    fun onSearchInOff_whenOffline_keepsLocalResultsAndShowsOfflineMessage() = runTest {
        fakeOffClient.searchResultToReturn = OffSearchByNameResult.Offline

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("Avena")
        advanceUntilIdle()

        // Local results exist
        assertEquals(1, viewModel.uiState.value.searchResults.size)

        viewModel.onSearchInOff()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Local results still present!
        assertEquals(1, state.searchResults.size)
        assertTrue(state.offSearchResults.isEmpty())
        assertNotNull(state.offSearchMessage)
        assertTrue(state.offSearchMessage!!.contains("Sin conexión a internet"))
    }

    @Test
    fun onSearchInOff_whenRateLimited_showsRateLimitMessageWithRemainingSeconds() = runTest {
        fakeOffClient.searchResultToReturn = OffSearchByNameResult.RateLimited(secondsRemaining = 42)

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("avena")
        advanceUntilIdle()

        viewModel.onSearchInOff()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.offSearchMessage)
        assertTrue(state.offSearchMessage!!.contains("42 s"))
    }

    @Test
    fun onSelectOffProduct_whenProductExistsLocally_usesLocalProductWithoutNetwork() = runTest {
        val existingLocal = sampleProduct.copy(barcode = "8410000000001")
        fakeProductRepository.products.clear()
        fakeProductRepository.products.add(existingLocal)

        val offProduct = OffProduct(
            barcode = "8410000000001",
            name = "Avena copos OFF",
            brand = null,
            kcal100g = 370.0,
            protein100g = 13.5,
            carbs100g = 60.0,
            fat100g = 7.0,
        )
        val item = OffSearchItem(
            code = "8410000000001",
            name = "Avena copos OFF",
            brand = null,
            kcal100g = 370.0,
            offProduct = offProduct,
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        var navigatedToConfirmation = false
        viewModel.onSelectOffProduct(item) {
            navigatedToConfirmation = true
        }
        advanceUntilIdle()

        // Network was NOT called
        assertEquals(0, fakeOffClient.barcodeCallCount)
        // Did NOT navigate to confirmation (used local!)
        assertTrue(!navigatedToConfirmation)
        // Opened portion dialog for local product
        val selected = viewModel.uiState.value.selectedProductForPortion
        assertNotNull(selected)
        assertEquals(existingLocal.id, selected!!.id)
    }

    @Test
    fun onSelectOffProduct_whenNotLocalAndMissingNutrients_fetchesProductCard() = runTest {
        val incompleteOffProduct = OffProduct(
            barcode = "8410000000099",
            name = "Producto incompleto",
            brand = null,
            kcal100g = 200.0,
            protein100g = null, // Missing mandatory
            carbs100g = null,
            fat100g = null,
        )
        val fullOffProduct = OffProduct(
            barcode = "8410000000099",
            name = "Producto completo",
            brand = "Marca",
            kcal100g = 200.0,
            protein100g = 10.0,
            carbs100g = 20.0,
            fat100g = 5.0,
        )
        fakeOffClient.productResultToReturn = OffSearchResult.Success(fullOffProduct)

        val item = OffSearchItem(
            code = "8410000000099",
            name = "Producto incompleto",
            brand = null,
            kcal100g = 200.0,
            offProduct = incompleteOffProduct,
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        var confirmedProduct: OffProduct? = null
        viewModel.onSelectOffProduct(item) {
            confirmedProduct = it
        }
        advanceUntilIdle()

        assertEquals(1, fakeOffClient.barcodeCallCount)
        val result = checkNotNull(confirmedProduct)
        assertEquals("Producto completo", result.name)
        assertEquals(10.0, result.protein100g ?: 0.0, 0.001)
    }

    @Test
    fun onSelectOffProduct_whenNotLocalAndCompleteNutrients_navigatesWithoutProductCardFetch() = runTest {
        val completeOffProduct = OffProduct(
            barcode = "8410000000088",
            name = "Producto completo",
            brand = "Marca",
            kcal100g = 200.0,
            protein100g = 10.0,
            carbs100g = 20.0,
            fat100g = 5.0,
        )
        val item = OffSearchItem(
            code = "8410000000088",
            name = "Producto completo",
            brand = "Marca",
            kcal100g = 200.0,
            offProduct = completeOffProduct,
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        var confirmedProduct: OffProduct? = null
        viewModel.onSelectOffProduct(item) {
            confirmedProduct = it
        }
        advanceUntilIdle()

        assertEquals(0, fakeOffClient.barcodeCallCount)
        val result = checkNotNull(confirmedProduct)
        assertEquals("Producto completo", result.name)
    }
}

private class FakeOffClient : OffClient {
    var searchResultToReturn: OffSearchByNameResult = OffSearchByNameResult.Empty
    var productResultToReturn: OffSearchResult = OffSearchResult.NotFound
    var lastSearchedQuery: String? = null
    var searchCallCount = 0
    var barcodeCallCount = 0

    override suspend fun getProductByBarcode(barcode: String): OffSearchResult {
        barcodeCallCount++
        return productResultToReturn
    }

    override suspend fun searchProductsByName(query: String): OffSearchByNameResult {
        searchCallCount++
        lastSearchedQuery = query
        return searchResultToReturn
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
