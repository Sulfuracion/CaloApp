package com.controlcal.app.ui.detail

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeMealEntryRepository: FakeMealEntryRepository
    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var fakeMealConfigRepository: FakeMealConfigRepository
    private lateinit var viewModel: MealDetailViewModel

    private val testClock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("UTC"))
    private val testDate = LocalDate.of(2026, 10, 15)

    private val sampleProduct = Product(
        id = 1L,
        name = "Arroz blanco",
        kcal100g = 130.0,
        protein100g = 2.7,
        carbs100g = 28.0,
        fat100g = 0.3,
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

        viewModel = MealDetailViewModel(
            mealEntryRepository = fakeMealEntryRepository,
            mealConfigRepository = fakeMealConfigRepository,
            productRepository = fakeProductRepository,
            clock = testClock,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialize_loadsEntriesAndTotalsForSelectedMeal() = runTest {
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-15",
                meal = DefaultMealIds.LUNCH,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 200.0,
                kcal = 260.0,
                protein = 5.4,
                carbs = 56.0,
                fat = 0.6,
                loggedAt = 1000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DefaultMealIds.LUNCH, state.currentMealId)
        assertEquals("Almuerzo", state.currentMealName)
        assertEquals(1, state.entries.size)
        assertEquals(260.0, state.totals.kcal, 0.001)
        assertEquals("200 g", state.entries[0].formattedQuantity)
        assertFalse(state.entries[0].isTare)
    }

    @Test
    fun formattedQuantity_usesUnitsWhenProductHasUnitG() = runTest {
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 2L,
                date = "2026-10-15",
                meal = DefaultMealIds.BREAKFAST,
                productId = sampleProductWithUnit.id,
                productName = sampleProductWithUnit.name,
                grams = 110.0,
                kcal = 165.0,
                protein = 13.75,
                carbs = 0.55,
                fat = 11.0,
                loggedAt = 1000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("2 huevo (110 g)", state.entries[0].formattedQuantity)
    }

    @Test
    fun switchMeal_changesDisplayedMealWithoutMovingEntries() = runTest {
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-15",
                meal = DefaultMealIds.BREAKFAST,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 100.0,
                kcal = 130.0,
                protein = 2.7,
                carbs = 28.0,
                fat = 0.3,
                loggedAt = 1000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.entries.size)

        // Switch to Dinner without exiting
        viewModel.onSwitchMeal(DefaultMealIds.DINNER)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DefaultMealIds.DINNER, state.currentMealId)
        assertEquals("Cena", state.currentMealName)
        assertEquals(0, state.entries.size)
        assertEquals(0.0, state.totals.kcal, 0.001)

        // Verify the original entry is still in breakfast in the repository
        val breakfastEntries = fakeMealEntryRepository.getByDateAndMeal("2026-10-15", DefaultMealIds.BREAKFAST)
        assertEquals(1, breakfastEntries.size)
    }

    @Test
    fun deleteEntry_removesItAndAllowsUndo() = runTest {
        val entry = MealEntry(
            id = 1L,
            date = "2026-10-15",
            meal = DefaultMealIds.LUNCH,
            productId = sampleProduct.id,
            productName = sampleProduct.name,
            grams = 100.0,
            kcal = 130.0,
            protein = 2.7,
            carbs = 28.0,
            fat = 0.3,
            loggedAt = 1000L,
        )
        fakeMealEntryRepository.insert(entry)

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.entries.size)

        // Delete entry
        viewModel.onDeleteEntry(entry)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertTrue(fakeMealEntryRepository.entries.isEmpty())
        assertNotNull(viewModel.uiState.value.recentlyDeletedEntry)

        // Undo delete
        viewModel.onUndoDelete()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)
        assertEquals(1, fakeMealEntryRepository.entries.size)
    }

    @Test
    fun tareMode_createsNegativeEntryAndReducesCaloriesWithoutDroppingBelowZero() = runTest {
        val entry = MealEntry(
            id = 1L,
            date = "2026-10-15",
            meal = DefaultMealIds.LUNCH,
            productId = sampleProduct.id,
            productName = sampleProduct.name,
            grams = 100.0,
            kcal = 130.0,
            protein = 2.7,
            carbs = 28.0,
            fat = 0.3,
            loggedAt = 1000L,
        )
        fakeMealEntryRepository.insert(entry)

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        viewModel.onOpenTareDialog()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showTareDialog)

        viewModel.onTareGramsChanged("50")
        viewModel.onConfirmTare()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.entries.size)
        val tare = state.entries.find { it.isTare }
        assertNotNull(tare)
        assertEquals(-50.0, tare!!.entry.grams, 0.001)
        assertEquals(65.0, state.totals.kcal, 0.001)

        // Even if more leftovers are subtracted than consumed, totals clamp to 0.0
        viewModel.onOpenTareDialog()
        advanceUntilIdle()
        viewModel.onTareGramsChanged("200")
        viewModel.onConfirmTare()
        advanceUntilIdle()

        assertEquals(0.0, viewModel.uiState.value.totals.kcal, 0.001)
    }

    @Test
    fun repeatMeal_clonesPreviousDayEntriesForCurrentMeal() = runTest {
        val yesterday = "2026-10-14"
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 10L,
                date = yesterday,
                meal = DefaultMealIds.LUNCH,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 150.0,
                kcal = 195.0,
                protein = 4.05,
                carbs = 42.0,
                fat = 0.45,
                loggedAt = 1000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.entries.isEmpty())

        viewModel.onRepeatMeal()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.entries.size)
        assertEquals(195.0, state.totals.kcal, 0.001)
    }

    @Test
    fun increaseEntryQuantity_increasesQuantityByStepAndRecalculatesTotals() = runTest {
        val entry = MealEntry(
            id = 1L,
            date = "2026-10-15",
            meal = DefaultMealIds.LUNCH,
            productId = sampleProduct.id,
            productName = sampleProduct.name,
            grams = 100.0,
            kcal = 130.0,
            protein = 2.7,
            carbs = 28.0,
            fat = 0.3,
            loggedAt = 1000L,
        )
        fakeMealEntryRepository.insert(entry)

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        // sampleProduct has neither unitG nor servingG -> step is 10g
        viewModel.onIncreaseEntryQuantity(entry)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(110.0, state.entries[0].entry.grams, 0.001)
        assertEquals(143.0, state.entries[0].entry.kcal, 0.001)
        assertEquals(143.0, state.totals.kcal, 0.001)
        assertTrue(state.entries[0].canDecrease)
    }

    @Test
    fun increaseEntryQuantity_withUnitG_increasesByUnitStep() = runTest {
        val entry = MealEntry(
            id = 2L,
            date = "2026-10-15",
            meal = DefaultMealIds.BREAKFAST,
            productId = sampleProductWithUnit.id,
            productName = sampleProductWithUnit.name,
            grams = 55.0,
            kcal = 82.5,
            protein = 6.875,
            carbs = 0.275,
            fat = 5.5,
            loggedAt = 1000L,
        )
        fakeMealEntryRepository.insert(entry)

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        // sampleProductWithUnit has unitG = 55.0 -> step is 55g
        assertEquals(55.0, viewModel.uiState.value.entries[0].stepGrams, 0.001)
        assertFalse(viewModel.uiState.value.entries[0].canDecrease) // At 1 step, cannot decrease

        viewModel.onIncreaseEntryQuantity(entry)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(110.0, state.entries[0].entry.grams, 0.001)
        assertEquals(165.0, state.entries[0].entry.kcal, 0.001)
        assertEquals("2 huevo (110 g)", state.entries[0].formattedQuantity)
        assertTrue(state.entries[0].canDecrease) // Now at 2 steps, can decrease
    }

    @Test
    fun decreaseEntryQuantity_clampsAtOneStepAndNeverDropsBelow() = runTest {
        val entry = MealEntry(
            id = 1L,
            date = "2026-10-15",
            meal = DefaultMealIds.LUNCH,
            productId = sampleProduct.id,
            productName = sampleProduct.name,
            grams = 20.0,
            kcal = 26.0,
            protein = 0.54,
            carbs = 5.6,
            fat = 0.06,
            loggedAt = 1000L,
        )
        fakeMealEntryRepository.insert(entry)

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.entries[0].canDecrease)

        // Step is 10g. 20g - 10g = 10g (exactly 1 step)
        viewModel.onDecreaseEntryQuantity(entry)
        advanceUntilIdle()

        val state1 = viewModel.uiState.value
        assertEquals(10.0, state1.entries[0].entry.grams, 0.001)
        assertEquals(13.0, state1.entries[0].entry.kcal, 0.001)
        assertFalse(state1.entries[0].canDecrease)

        // Decreasing again does NOT drop below 10g
        viewModel.onDecreaseEntryQuantity(state1.entries[0].entry)
        advanceUntilIdle()

        val state2 = viewModel.uiState.value
        assertEquals(10.0, state2.entries[0].entry.grams, 0.001)
        assertFalse(state2.entries[0].canDecrease)
    }

    @Test
    fun mealTime_resolvedFromEarliestEntry() = runTest {
        val time0815 = testDate.atTime(java.time.LocalTime.of(8, 15)).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val time0900 = testDate.atTime(java.time.LocalTime.of(9, 0)).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()

        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-15",
                meal = DefaultMealIds.BREAKFAST,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 100.0,
                kcal = 130.0,
                protein = 2.7,
                carbs = 28.0,
                fat = 0.3,
                loggedAt = time0900,
            )
        )
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 2L,
                date = "2026-10-15",
                meal = DefaultMealIds.BREAKFAST,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 50.0,
                kcal = 65.0,
                protein = 1.35,
                carbs = 14.0,
                fat = 0.15,
                loggedAt = time0815,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(java.time.LocalTime.of(8, 15), state.mealTime)
        assertEquals("08:15", state.formattedMealTime)
    }

    @Test
    fun onMealTimeChanged_updatesLoggedAtOfAllEntriesInMealAndDay() = runTest {
        val originalLoggedAt = testDate.atTime(java.time.LocalTime.of(8, 0)).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()

        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-15",
                meal = DefaultMealIds.BREAKFAST,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 100.0,
                kcal = 130.0,
                protein = 2.7,
                carbs = 28.0,
                fat = 0.3,
                loggedAt = originalLoggedAt,
            )
        )
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 2L,
                date = "2026-10-15",
                meal = DefaultMealIds.BREAKFAST,
                productId = sampleProductWithUnit.id,
                productName = sampleProductWithUnit.name,
                grams = 55.0,
                kcal = 82.5,
                protein = 6.875,
                carbs = 0.275,
                fat = 5.5,
                loggedAt = originalLoggedAt,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.BREAKFAST)
        advanceUntilIdle()

        val newTime = java.time.LocalTime.of(9, 30)
        viewModel.onMealTimeChanged(newTime)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(newTime, state.mealTime)
        assertEquals("09:30", state.formattedMealTime)
        assertFalse(state.showTimePickerDialog)

        // Verify repository entries all updated
        val updatedEntries = fakeMealEntryRepository.getByDateAndMeal("2026-10-15", DefaultMealIds.BREAKFAST)
        assertEquals(2, updatedEntries.size)
        val expectedLoggedAt = testDate.atTime(newTime).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(expectedLoggedAt, updatedEntries[0].loggedAt)
        assertEquals(expectedLoggedAt, updatedEntries[1].loggedAt)
    }

    @Test
    fun addQuickCalories_insertsEntryWithNullProductIdAndZeroGrams_andShowsInList() = runTest {
        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        viewModel.onOpenAddQuickCaloriesDialog()
        assertTrue(viewModel.uiState.value.showQuickCaloriesDialog)
        assertFalse(viewModel.uiState.value.isQuickCaloriesSaveEnabled)

        viewModel.onQuickKcalChanged("250")
        viewModel.onQuickProteinChanged("10")
        viewModel.onQuickCarbsChanged("30")
        viewModel.onQuickFatChanged("10")
        viewModel.onQuickDescriptionChanged("Café bombón")
        assertTrue(viewModel.uiState.value.isQuickCaloriesSaveEnabled)

        viewModel.onConfirmQuickCalories()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showQuickCaloriesDialog)
        val saved = fakeMealEntryRepository.entries.single()
        kotlin.test.assertNull(saved.productId)
        assertEquals("Café bombón", saved.productName)
        assertEquals(0.0, saved.grams, 0.0)
        assertEquals(250.0, saved.kcal, 0.0)
        assertEquals(10.0, saved.protein, 0.0)
        assertEquals(30.0, saved.carbs, 0.0)
        assertEquals(10.0, saved.fat, 0.0)

        val uiEntry = viewModel.uiState.value.entries.single()
        assertTrue(uiEntry.isQuickCalories)
        assertEquals("", uiEntry.formattedQuantity)
        assertFalse(uiEntry.canDecrease)
        assertEquals("250 kcal", uiEntry.formattedKcal)
    }

    @Test
    fun editQuickCalories_updatesExistingEntry() = runTest {
        val entryId = fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-15",
                meal = DefaultMealIds.LUNCH,
                productId = null,
                productName = "Calorías rápidas",
                grams = 0.0,
                kcal = 150.0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                loggedAt = 1000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        val existing = fakeMealEntryRepository.entries.single()
        viewModel.onOpenEditQuickCaloriesDialog(existing)
        assertTrue(viewModel.uiState.value.showQuickCaloriesDialog)
        assertEquals("150", viewModel.uiState.value.quickKcalText)

        viewModel.onQuickKcalChanged("200")
        viewModel.onQuickDescriptionChanged("Postre casero")
        viewModel.onConfirmQuickCalories()
        advanceUntilIdle()

        val updated = fakeMealEntryRepository.entries.single()
        assertEquals(entryId, updated.id)
        kotlin.test.assertNull(updated.productId)
        assertEquals("Postre casero", updated.productName)
        assertEquals(200.0, updated.kcal, 0.0)
    }

    @Test
    fun quickCalories_cannotDecreaseOrIncrease() = runTest {
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-15",
                meal = DefaultMealIds.LUNCH,
                productId = null,
                productName = "Calorías rápidas",
                grams = 0.0,
                kcal = 150.0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                loggedAt = 1000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        val entry = fakeMealEntryRepository.entries.single()
        viewModel.onIncreaseEntryQuantity(entry)
        advanceUntilIdle()
        assertEquals(150.0, fakeMealEntryRepository.entries.single().kcal, 0.0)

        viewModel.onDecreaseEntryQuantity(entry)
        advanceUntilIdle()
        assertEquals(150.0, fakeMealEntryRepository.entries.single().kcal, 0.0)
    }

    @Test
    fun quickCalories_notEligibleForTareDialog() = runTest {
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-15",
                meal = DefaultMealIds.LUNCH,
                productId = null,
                productName = "Calorías rápidas",
                grams = 0.0,
                kcal = 150.0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                loggedAt = 1000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        viewModel.onOpenTareDialog()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showTareDialog)
        assertEquals("No hay alimentos en esta comida para restar sobras", viewModel.uiState.value.userMessage)
    }

    @Test
    fun dayWithNormalTareAndQuickCalories_allDisplayedAndCalculated() = runTest {
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-15",
                meal = DefaultMealIds.LUNCH,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = 200.0,
                kcal = 260.0,
                protein = 5.4,
                carbs = 56.0,
                fat = 0.6,
                loggedAt = 1000L,
            )
        )
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 2L,
                date = "2026-10-15",
                meal = DefaultMealIds.LUNCH,
                productId = sampleProduct.id,
                productName = sampleProduct.name,
                grams = -50.0,
                kcal = -65.0,
                protein = -1.35,
                carbs = -14.0,
                fat = -0.15,
                loggedAt = 2000L,
            )
        )
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 3L,
                date = "2026-10-15",
                meal = DefaultMealIds.LUNCH,
                productId = null,
                productName = "Calorías rápidas",
                grams = 0.0,
                kcal = 150.0,
                protein = 4.0,
                carbs = 10.0,
                fat = 2.0,
                loggedAt = 3000L,
            )
        )

        viewModel.initialize(testDate, DefaultMealIds.LUNCH)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.entries.size)

        // 260 - 65 + 150 = 345 kcal
        assertEquals(345.0, state.totals.kcal, 0.01)
        assertEquals(8.05, state.totals.protein, 0.01)
        assertEquals(52.0, state.totals.carbs, 0.01)
        assertEquals(2.45, state.totals.fat, 0.01)
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
