package com.controlcal.app.ui.tetris

import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.nutrition.CalculateDayRemainingUseCase
import com.controlcal.core.nutrition.GetActiveGoalProfileUseCase
import com.controlcal.core.nutrition.ResolveDayTypeUseCase
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import com.controlcal.core.repo.UserSettingsRepository
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
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMeals
import com.controlcal.core.repo.MealConfigRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MacroTetrisViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var fakeMealEntryRepository: FakeMealEntryRepository
    private lateinit var fakeDailyLogRepository: FakeDailyLogRepository
    private lateinit var fakeUserSettingsRepository: FakeUserSettingsRepository
    private lateinit var fakeGoalProfileRepository: FakeGoalProfileRepository
    private lateinit var fakeMealConfigRepository: FakeMealConfigRepository
    private lateinit var viewModel: MacroTetrisViewModel

    private val today = LocalDate.of(2026, 10, 14)
    private val todayStr = today.toString()

    private val chickenBreast = Product(
        id = 1L,
        name = "Pechuga de pollo",
        kcal100g = 120.0,
        protein100g = 26.0,
        carbs100g = 0.0,
        fat100g = 1.5,
        inPantry = true,
        source = ProductSource.MANUAL,
    )

    private val rice = Product(
        id = 2L,
        name = "Arroz blanco",
        kcal100g = 350.0,
        protein100g = 7.0,
        carbs100g = 78.0,
        fat100g = 0.8,
        inPantry = true,
        source = ProductSource.MANUAL,
    )

    private val oliveOil = Product(
        id = 3L,
        name = "Aceite de oliva virgen extra",
        kcal100g = 884.0,
        protein100g = 0.0,
        carbs100g = 0.0,
        fat100g = 100.0,
        inPantry = true,
        maxG = 30.0,
        source = ProductSource.MANUAL,
    )

    private val egg = Product(
        id = 4L,
        name = "Huevo campero",
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeProductRepository = FakeProductRepository().apply {
            products.addAll(listOf(chickenBreast, rice, oliveOil, egg))
        }
        fakeMealEntryRepository = FakeMealEntryRepository()
        fakeDailyLogRepository = FakeDailyLogRepository()
        fakeUserSettingsRepository = FakeUserSettingsRepository()
        fakeGoalProfileRepository = FakeGoalProfileRepository().apply {
            profiles.add(
                GoalProfile(
                    id = 1L,
                    dayType = DayType.TRAINING,
                    kcal = 2000.0,
                    proteinG = 150.0,
                    carbsG = 220.0,
                    fatG = 60.0,
                    validFrom = "2026-01-01",
                    origin = GoalOrigin.MANUAL,
                )
            )
        }

        fakeDailyLogRepository.logs[todayStr] = DailyLog(date = todayStr, dayType = DayType.TRAINING)

        val resolveDayTypeUseCase = ResolveDayTypeUseCase(fakeDailyLogRepository, fakeUserSettingsRepository)
        val getActiveGoalProfileUseCase = GetActiveGoalProfileUseCase(fakeGoalProfileRepository)
        val calculateDayRemainingUseCase = CalculateDayRemainingUseCase()
        fakeMealConfigRepository = FakeMealConfigRepository()

        viewModel = MacroTetrisViewModel(
            productRepository = fakeProductRepository,
            mealEntryRepository = fakeMealEntryRepository,
            resolveDayTypeUseCase = resolveDayTypeUseCase,
            getActiveGoalProfileUseCase = getActiveGoalProfileUseCase,
            mealConfigRepository = fakeMealConfigRepository,
            calculateDayRemainingUseCase = calculateDayRemainingUseCase,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadData_populatesRemainingAndCalculatesInitialSolution() = runTest {
        viewModel.loadData(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.todayRemaining)
        assertEquals(2000.0, state.targetMacros.kcal, 0.01)
        assertEquals(150.0, state.targetMacros.protein, 0.01)
        assertEquals(220.0, state.targetMacros.carbs, 0.01)
        assertEquals(60.0, state.targetMacros.fat, 0.01)
        assertEquals(4, state.pantryProducts.size)
        assertTrue(state.hasCalculated)

        val solution = state.solution
        assertNotNull(solution)
        assertTrue(solution!!.items.isNotEmpty())
        assertTrue(solution.items.size <= 5)
    }

    @Test
    fun excludeProduct_recalculatesWithoutExcludedItem() = runTest {
        viewModel.loadData(today)
        advanceUntilIdle()

        val initialItems = viewModel.uiState.value.solution!!.items
        assertTrue(initialItems.any { it.product.id == chickenBreast.id })

        viewModel.excludeProduct(chickenBreast.id)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertTrue(updatedState.excludedProductIds.contains(chickenBreast.id))
        assertEquals(listOf(chickenBreast), updatedState.excludedProducts)

        val updatedSolution = updatedState.solution
        assertNotNull(updatedSolution)
        assertFalse(updatedSolution!!.items.any { it.product.id == chickenBreast.id })
    }

    @Test
    fun includeProduct_restoresPreviouslyExcludedItem() = runTest {
        viewModel.loadData(today)
        advanceUntilIdle()

        viewModel.excludeProduct(chickenBreast.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.excludedProductIds.contains(chickenBreast.id))

        viewModel.includeProduct(chickenBreast.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.excludedProductIds.contains(chickenBreast.id))
        assertTrue(state.excludedProducts.isEmpty())
        assertTrue(state.solution!!.items.any { it.product.id == chickenBreast.id })
    }

    @Test
    fun selectMeal_updatesSelectedMeal() = runTest {
        viewModel.selectMeal(DefaultMealIds.DINNER)
        assertEquals(DefaultMealIds.DINNER, viewModel.uiState.value.selectedMeal)

        viewModel.selectMeal(DefaultMealIds.BREAKFAST)
        assertEquals(DefaultMealIds.BREAKFAST, viewModel.uiState.value.selectedMeal)
    }

    @Test
    fun addToDiary_insertsAllPortionsIntoMealEntryRepository() = runTest {
        viewModel.loadData(today)
        advanceUntilIdle()

        viewModel.selectMeal(DefaultMealIds.DINNER)

        var successCalled = false
        viewModel.addToDiary(today) {
            successCalled = true
        }
        advanceUntilIdle()

        assertTrue(successCalled)
        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertFalse(state.isAddingToDiary)

        val savedEntries = fakeMealEntryRepository.entries
        assertTrue(savedEntries.isNotEmpty())
        assertTrue(savedEntries.all { it.date == todayStr })
        assertTrue(savedEntries.all { it.meal == DefaultMealIds.DINNER })
        assertTrue(savedEntries.all { it.grams > 0.0 })
        assertTrue(savedEntries.all { it.kcal > 0.0 })
    }

    @Test
    fun errorAndSuggestions_reportedWhenPantryCannotCoverMacros() = runTest {
        // Pantry with only carbs (rice)
        fakeProductRepository.products.clear()
        fakeProductRepository.products.add(rice)

        viewModel.loadData(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val solution = state.solution
        assertNotNull(solution)
        assertTrue(solution!!.error.percentProtein > 10.0)
        assertTrue(solution.suggestions.isNotEmpty())
        assertTrue(solution.suggestions.any { it.macro == "Proteína" })
    }

    @Test
    fun emptyPantry_producesEmptySolutionAndFlagsPantryEmpty() = runTest {
        fakeProductRepository.products.clear()

        viewModel.loadData(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isPantryEmpty)
        val solution = state.solution
        assertNotNull(solution)
        assertTrue(solution!!.items.isEmpty())
        assertTrue(solution.suggestions.any { it.suggestionType == com.controlcal.core.solver.MacroSuggestionType.EMPTY_PANTRY })
    }

    @Test
    fun targetsAlreadyMet_returnsEmptySolution() = runTest {
        // Target profile with 0 macros
        fakeGoalProfileRepository.profiles.clear()
        fakeGoalProfileRepository.profiles.add(
            GoalProfile(
                id = 2L,
                dayType = DayType.TRAINING,
                kcal = 0.0,
                proteinG = 0.0,
                carbsG = 0.0,
                fatG = 0.0,
                validFrom = "2026-01-01",
                origin = GoalOrigin.MANUAL,
            )
        )

        viewModel.loadData(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.areTargetsMet)
        assertNotNull(state.solution)
        assertTrue(state.solution!!.items.isEmpty())
    }

    @Test
    fun loadData_populatesAvailableMealsAndPreselectsSuggestedMeal() = runTest {
        viewModel.loadData(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(4, state.availableMeals.size)
        assertTrue(state.availableMeals.all { it.isActive })
        assertTrue(state.availableMeals.any { it.id == state.selectedMeal })
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

    override suspend fun getByDate(date: String): List<MealEntry> =
        entries.filter { it.date == date }

    override suspend fun getByDateAndMeal(date: String, meal: String): List<MealEntry> =
        entries.filter { it.date == date && it.meal == meal }

    override suspend fun getByProduct(productId: Long): List<MealEntry> =
        entries.filter { it.productId == productId }

    override suspend fun getSince(sinceMillis: Long): List<MealEntry> =
        entries.filter { it.loggedAt >= sinceMillis }

    override suspend fun getLatestDateWithMealBefore(date: String, meal: String): String? = null
    override suspend fun countByMeal(meal: String): Int = entries.count { it.meal == meal }
}

private class FakeDailyLogRepository : DailyLogRepository {
    val logs = mutableMapOf<String, DailyLog>()

    override suspend fun getByDate(date: String): DailyLog? = logs[date]
    override suspend fun insert(log: DailyLog) { logs[log.date] = log }
    override suspend fun setWaterMl(date: String, waterMl: Int) {}
    override suspend fun setDayType(date: String, dayType: DayType) {
        val existing = logs[date]
        logs[date] = existing?.copy(dayType = dayType) ?: DailyLog(date = date, dayType = dayType)
    }
    override suspend fun setNotes(date: String, notes: String?) {}
}

private class FakeUserSettingsRepository : UserSettingsRepository {
    var pattern = WeeklyDayPattern()
    var minKcal = 1200.0
    var lastProposalDate: String? = null

    override suspend fun getWeeklyPattern(): WeeklyDayPattern = pattern
    override suspend fun setWeeklyPattern(pattern: WeeklyDayPattern) { this.pattern = pattern }
    override suspend fun getMinKcalLimit(): Double = minKcal
    override suspend fun setMinKcalLimit(minKcal: Double) { this.minKcal = minKcal }
    override suspend fun getLastTdeeProposalReviewedDate(): String? = lastProposalDate
    override suspend fun setLastTdeeProposalReviewedDate(date: String) { lastProposalDate = date }
}

private class FakeGoalProfileRepository : GoalProfileRepository {
    val profiles = mutableListOf<GoalProfile>()

    override suspend fun insert(profile: GoalProfile): Long {
        val id = if (profile.id == 0L) (profiles.size + 1).toLong() else profile.id
        profiles.add(profile.copy(id = id))
        return id
    }

    override suspend fun getProfileForDayType(dayType: DayType, date: String): GoalProfile? {
        return profiles
            .filter { it.dayType == dayType && it.validFrom <= date }
            .sortedWith(compareByDescending<GoalProfile> { it.validFrom }.thenByDescending { it.id })
            .firstOrNull()
    }

    override suspend fun getProfilesForDayTypeUpTo(dayType: DayType, date: String): List<GoalProfile> {
        return profiles
            .filter { it.dayType == dayType && it.validFrom <= date }
            .sortedWith(compareByDescending<GoalProfile> { it.validFrom }.thenByDescending { it.id })
    }
}

private class FakeMealConfigRepository(
    initialConfigs: List<ConfiguredMeal> = DefaultMeals.defaults()
) : MealConfigRepository {
    var configs = initialConfigs.toMutableList()

    override suspend fun getMealConfigs(): List<ConfiguredMeal> = configs.toList()
    override suspend fun saveMealConfigs(configs: List<ConfiguredMeal>) {
        this.configs = configs.toMutableList()
    }
}
