package com.controlcal.app.ui.panel

import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import com.controlcal.core.suggest.QuickLogSuggestion
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
import java.time.LocalDate

import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMeals
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.nutrition.GetActiveGoalProfileUseCase
import com.controlcal.core.nutrition.ResolveDayTypeUseCase
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealConfigRepository
import com.controlcal.core.repo.UserSettingsRepository
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class DailyPanelViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeMealEntryRepository: FakeMealEntryRepository
    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var fakeDailyLogRepository: FakeDailyLogRepository
    private lateinit var fakeUserSettingsRepository: FakeUserSettingsRepository
    private lateinit var fakeGoalProfileRepository: FakeGoalProfileRepository
    private lateinit var fakeWeightEntryRepository: FakeWeightEntryRepository
    private lateinit var fakeMealConfigRepository: FakeMealConfigRepository
    private lateinit var viewModel: DailyPanelViewModel

    private val testProduct = Product(
        id = 10L,
        name = "Avena Copos",
        kcal100g = 370.0,
        protein100g = 13.5,
        carbs100g = 60.0,
        fat100g = 7.0,
        source = ProductSource.MANUAL,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeMealEntryRepository = FakeMealEntryRepository()
        fakeProductRepository = FakeProductRepository()
        fakeDailyLogRepository = FakeDailyLogRepository()
        fakeUserSettingsRepository = FakeUserSettingsRepository()
        fakeGoalProfileRepository = FakeGoalProfileRepository()
        fakeWeightEntryRepository = FakeWeightEntryRepository()
        fakeMealConfigRepository = FakeMealConfigRepository()
        fakeProductRepository.products.add(testProduct)

        val resolveDayTypeUseCase = ResolveDayTypeUseCase(fakeDailyLogRepository, fakeUserSettingsRepository)
        val getActiveGoalProfileUseCase = GetActiveGoalProfileUseCase(fakeGoalProfileRepository)

        viewModel = DailyPanelViewModel(
            mealEntryRepository = fakeMealEntryRepository,
            productRepository = fakeProductRepository,
            dailyLogRepository = fakeDailyLogRepository,
            resolveDayTypeUseCase = resolveDayTypeUseCase,
            getActiveGoalProfileUseCase = getActiveGoalProfileUseCase,
            weightEntryRepository = fakeWeightEntryRepository,
            userSettingsRepository = fakeUserSettingsRepository,
            goalProfileRepository = fakeGoalProfileRepository,
            mealConfigRepository = fakeMealConfigRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDay_withMatchingRecentEntries_populatesSuggestions() = runTest {
        val now = System.currentTimeMillis()
        val recentEntry = MealEntry(
            id = 1L,
            date = LocalDate.now().minusDays(1).toString(),
            meal = DefaultMealIds.BREAKFAST,
            productId = testProduct.id,
            productName = testProduct.name,
            grams = 50.0,
            kcal = 185.0,
            protein = 6.75,
            carbs = 30.0,
            fat = 3.5,
            loggedAt = now - (24L * 60 * 60 * 1000L),
        )
        fakeMealEntryRepository.entries.add(recentEntry)

        viewModel.loadDay(LocalDate.now())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.suggestions.size)
        assertEquals(testProduct.id, state.suggestions.first().productId)
        assertEquals(50.0, state.suggestions.first().defaultGrams, 0.001)
    }

    @Test
    fun onSuggestionTapped_addsEntryToTargetMealDirectly() = runTest {
        val suggestion = QuickLogSuggestion(
            productId = testProduct.id,
            productName = testProduct.name,
            defaultGrams = 40.0,
            score = 5.0,
            targetMeal = DefaultMealIds.BREAKFAST,
            lastLoggedAt = System.currentTimeMillis(),
        )

        viewModel.onSuggestionTapped(suggestion)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val breakfastEntries = state.entriesByMeal[DefaultMealIds.BREAKFAST] ?: emptyList()
        assertEquals(1, breakfastEntries.size)
        assertEquals("Avena Copos", breakfastEntries.first().productName)
        assertEquals(40.0, breakfastEntries.first().grams, 0.001)
        assertTrue(state.message?.contains("Avena Copos") == true)
    }

    @Test
    fun onSuggestionLongPressed_opensDialogWithGrams() = runTest {
        val suggestion = QuickLogSuggestion(
            productId = testProduct.id,
            productName = testProduct.name,
            defaultGrams = 60.0,
            score = 3.0,
            targetMeal = DefaultMealIds.BREAKFAST,
            lastLoggedAt = System.currentTimeMillis(),
        )

        viewModel.onSuggestionLongPressed(suggestion)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.editingSuggestion)
        assertEquals("60", state.editingGramsText)
        assertEquals(222.0, state.portionPreviewKcal, 0.01)
    }

    @Test
    fun onEditingGramsChanged_recalculatesPortionPreview() = runTest {
        val suggestion = QuickLogSuggestion(
            productId = testProduct.id,
            productName = testProduct.name,
            defaultGrams = 50.0,
            score = 3.0,
            targetMeal = DefaultMealIds.BREAKFAST,
            lastLoggedAt = System.currentTimeMillis(),
        )
        viewModel.onSuggestionLongPressed(suggestion)
        advanceUntilIdle()

        viewModel.onEditingGramsChanged("100")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(370.0, state.portionPreviewKcal, 0.01)
        assertEquals(13.5, state.portionPreviewProtein, 0.01)
        assertNull(state.editingGramsError)
    }

    @Test
    fun onConfirmQuickLog_savesModifiedQuantityAndClosesDialog() = runTest {
        val suggestion = QuickLogSuggestion(
            productId = testProduct.id,
            productName = testProduct.name,
            defaultGrams = 50.0,
            score = 3.0,
            targetMeal = DefaultMealIds.BREAKFAST,
            lastLoggedAt = System.currentTimeMillis(),
        )
        viewModel.onSuggestionLongPressed(suggestion)
        advanceUntilIdle()

        viewModel.onEditingGramsChanged("80")
        advanceUntilIdle()

        viewModel.onConfirmQuickLog()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.editingSuggestion)
        val entries = state.entriesByMeal[DefaultMealIds.BREAKFAST] ?: emptyList()
        assertEquals(1, entries.size)
        assertEquals(80.0, entries.first().grams, 0.001)
    }

    @Test
    fun onRepeatMeal_withPriorMeal_clonesEntries() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val priorEntry = MealEntry(
            id = 1L,
            date = yesterday.toString(),
            meal = DefaultMealIds.LUNCH,
            productId = testProduct.id,
            productName = testProduct.name,
            grams = 75.0,
            kcal = 277.5,
            protein = 10.12,
            carbs = 45.0,
            fat = 5.25,
            loggedAt = 1000L,
        )
        fakeMealEntryRepository.entries.add(priorEntry)

        viewModel.loadDay(today)
        advanceUntilIdle()

        viewModel.onRepeatMeal(DefaultMealIds.LUNCH)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val lunchEntries = state.entriesByMeal[DefaultMealIds.LUNCH] ?: emptyList()
        assertEquals(1, lunchEntries.size)
        assertEquals(today.toString(), lunchEntries.first().date)
        assertEquals(75.0, lunchEntries.first().grams, 0.001)
        assertTrue(state.message?.contains("Comida repetida") == true)
    }

    @Test
    fun onRepeatMeal_withoutPriorMeal_setsEmptyMessage() = runTest {
        viewModel.loadDay(LocalDate.now())
        advanceUntilIdle()

        viewModel.onRepeatMeal(DefaultMealIds.DINNER)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.entriesByMeal[DefaultMealIds.DINNER]?.isEmpty() == true)
        assertEquals("No hay registros anteriores de esta comida", state.message)
    }

    @Test
    fun onCopyYesterday_clonesAllYesterdayEntries() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val entry1 = MealEntry(
            id = 1L,
            date = yesterday.toString(),
            meal = DefaultMealIds.BREAKFAST,
            productId = testProduct.id,
            productName = testProduct.name,
            grams = 40.0,
            kcal = 148.0,
            protein = 5.4,
            carbs = 24.0,
            fat = 2.8,
            loggedAt = 1000L,
        )
        val entry2 = MealEntry(
            id = 2L,
            date = yesterday.toString(),
            meal = DefaultMealIds.DINNER,
            productId = testProduct.id,
            productName = testProduct.name,
            grams = 60.0,
            kcal = 222.0,
            protein = 8.1,
            carbs = 36.0,
            fat = 4.2,
            loggedAt = 2000L,
        )
        fakeMealEntryRepository.entries.addAll(listOf(entry1, entry2))

        viewModel.loadDay(today)
        advanceUntilIdle()

        viewModel.onCopyYesterday()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val todayBreakfast = state.entriesByMeal[DefaultMealIds.BREAKFAST] ?: emptyList()
        val todayDinner = state.entriesByMeal[DefaultMealIds.DINNER] ?: emptyList()
        assertEquals(1, todayBreakfast.size)
        assertEquals(1, todayDinner.size)
        assertTrue(state.message?.contains("Se han copiado las comidas de ayer") == true)
    }

    @Test
    fun onCopyYesterday_whenEmpty_setsEmptyMessage() = runTest {
        viewModel.loadDay(LocalDate.now())
        advanceUntilIdle()

        viewModel.onCopyYesterday()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("No hay registros en el día de ayer", state.message)
    }

    @Test
    fun dayNavigation_updatesSelectedDate() = runTest {
        val today = LocalDate.now()
        viewModel.loadDay(today)
        advanceUntilIdle()

        viewModel.onPreviousDay()
        advanceUntilIdle()
        assertEquals(today.minusDays(1), viewModel.uiState.value.selectedDate)

        viewModel.onNextDay()
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)

        viewModel.onPreviousDay()
        viewModel.onPreviousDay()
        viewModel.onSelectToday()
        advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun loadDay_resolvesDayTypeAndGoalProfileFromSchedule() = runTest {
        val monday = LocalDate.of(2026, 10, 12)
        fakeGoalProfileRepository.insert(
            GoalProfile(
                dayType = DayType.TRAINING,
                kcal = 2500.0,
                proteinG = 170.0,
                carbsG = 280.0,
                fatG = 75.0,
                validFrom = "2026-10-01",
            )
        )

        viewModel.loadDay(monday)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DayType.TRAINING, state.dayType)
        assertNotNull(state.activeGoalProfile)
        assertEquals(2500.0, state.activeGoalProfile!!.kcal, 0.001)
        assertNotNull(state.dayRemaining)
        assertEquals(2500.0, state.dayRemaining!!.remainingKcal, 0.001)
    }

    @Test
    fun onDayTypeChanged_persistsToDailyLogAndUpdatesActiveProfileAndRemaining() = runTest {
        val date = LocalDate.of(2026, 10, 12)
        fakeGoalProfileRepository.insert(
            GoalProfile(
                dayType = DayType.TRAINING,
                kcal = 2500.0,
                proteinG = 170.0,
                carbsG = 280.0,
                fatG = 75.0,
                validFrom = "2026-10-01",
            )
        )
        fakeGoalProfileRepository.insert(
            GoalProfile(
                dayType = DayType.REST,
                kcal = 2000.0,
                proteinG = 160.0,
                carbsG = 180.0,
                fatG = 70.0,
                validFrom = "2026-10-01",
            )
        )

        viewModel.loadDay(date)
        advanceUntilIdle()

        assertEquals(DayType.TRAINING, viewModel.uiState.value.dayType)
        assertEquals(2500.0, viewModel.uiState.value.activeGoalProfile!!.kcal, 0.001)

        // Switch to REST
        viewModel.onDayTypeChanged(DayType.REST)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DayType.REST, state.dayType)
        assertEquals(2000.0, state.activeGoalProfile!!.kcal, 0.001)
        assertEquals(DayType.REST, fakeDailyLogRepository.logs[date.toString()]?.dayType)
    }

    @Test
    fun tdeeProposal_whenSufficientDataAndNoRecentReview_showsProposalCard() = runTest {
        val today = LocalDate.now()
        // Provide 16 days of food intake in the last 21 days at 1700 kcal (creating a deficit > 150 kcal)
        for (i in 0..15) {
            val d = today.minusDays(i.toLong()).toString()
            fakeMealEntryRepository.insert(
                MealEntry(
                    id = (i + 1).toLong(),
                    date = d,
                    meal = DefaultMealIds.LUNCH,
                    productId = testProduct.id,
                    productName = testProduct.name,
                    grams = 100.0,
                    kcal = 1700.0,
                    protein = 50.0,
                    carbs = 200.0,
                    fat = 50.0,
                    loggedAt = System.currentTimeMillis(),
                )
            )
        }
        // Provide 10 days of weigh-ins in the last 21 days
        for (i in 0..9) {
            val d = today.minusDays(i.toLong()).toString()
            fakeWeightEntryRepository.upsert(
                com.controlcal.core.model.WeightEntry(date = d, weightKg = 75.0)
            )
        }

        viewModel.loadDay(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isWeeklyProposalVisible)
        val proposal = checkNotNull(state.tdeeProposal)
        val expectedProposalKcal = state.activeGoalProfile!!.kcal - 150.0
        assertEquals(expectedProposalKcal, proposal.proposedKcal, 1.0)
        assertTrue(proposal.isClampedByWeeklyLimit)
    }

    @Test
    fun tdeeProposal_whenAccepted_insertsNewGoalProfilesWithOriginTdeeAndUpdatesSettings() = runTest {
        val today = LocalDate.now()
        for (i in 0..15) {
            val d = today.minusDays(i.toLong()).toString()
            fakeMealEntryRepository.insert(
                MealEntry(
                    id = (i + 1).toLong(),
                    date = d,
                    meal = DefaultMealIds.LUNCH,
                    productId = testProduct.id,
                    productName = testProduct.name,
                    grams = 100.0,
                    kcal = 2100.0,
                    protein = 50.0,
                    carbs = 200.0,
                    fat = 50.0,
                    loggedAt = System.currentTimeMillis(),
                )
            )
        }
        for (i in 0..9) {
            val d = today.minusDays(i.toLong()).toString()
            fakeWeightEntryRepository.upsert(
                com.controlcal.core.model.WeightEntry(date = d, weightKg = 75.0)
            )
        }

        viewModel.loadDay(today)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isWeeklyProposalVisible)

        viewModel.onAcceptTdeeProposal()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isWeeklyProposalVisible)
        assertNull(state.tdeeProposal)
        assertEquals(today.toString(), fakeUserSettingsRepository.lastReviewedDate)

        val insertedTdeeProfiles = fakeGoalProfileRepository.profiles.filter { it.origin == GoalOrigin.TDEE }
        assertTrue(insertedTdeeProfiles.isNotEmpty())
    }

    @Test
    fun tdeeProposal_whenRejected_dismissesAndSetsReviewDateWithoutModifyingGoals() = runTest {
        val today = LocalDate.now()
        for (i in 0..15) {
            val d = today.minusDays(i.toLong()).toString()
            fakeMealEntryRepository.insert(
                MealEntry(
                    id = (i + 1).toLong(),
                    date = d,
                    meal = DefaultMealIds.LUNCH,
                    productId = testProduct.id,
                    productName = testProduct.name,
                    grams = 100.0,
                    kcal = 2000.0,
                    protein = 50.0,
                    carbs = 200.0,
                    fat = 50.0,
                    loggedAt = System.currentTimeMillis(),
                )
            )
        }
        for (i in 0..9) {
            val d = today.minusDays(i.toLong()).toString()
            fakeWeightEntryRepository.upsert(
                com.controlcal.core.model.WeightEntry(date = d, weightKg = 75.0)
            )
        }

        viewModel.loadDay(today)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isWeeklyProposalVisible)

        viewModel.onRejectTdeeProposal()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isWeeklyProposalVisible)
        assertNull(state.tdeeProposal)
        assertEquals(today.toString(), fakeUserSettingsRepository.lastReviewedDate)

        val insertedTdeeProfiles = fakeGoalProfileRepository.profiles.filter { it.origin == GoalOrigin.TDEE }
        assertTrue(insertedTdeeProfiles.isEmpty())
    }

    @Test
    fun tdeeProposal_whenReviewedRecently_doesNotShowProposalCard() = runTest {
        val today = LocalDate.now()
        fakeUserSettingsRepository.lastReviewedDate = today.minusDays(3).toString()

        for (i in 0..15) {
            val d = today.minusDays(i.toLong()).toString()
            fakeMealEntryRepository.insert(
                MealEntry(
                    id = (i + 1).toLong(),
                    date = d,
                    meal = DefaultMealIds.LUNCH,
                    productId = testProduct.id,
                    productName = testProduct.name,
                    grams = 100.0,
                    kcal = 2000.0,
                    protein = 50.0,
                    carbs = 200.0,
                    fat = 50.0,
                    loggedAt = System.currentTimeMillis(),
                )
            )
        }
        for (i in 0..9) {
            val d = today.minusDays(i.toLong()).toString()
            fakeWeightEntryRepository.upsert(
                com.controlcal.core.model.WeightEntry(date = d, weightKg = 75.0)
            )
        }

        viewModel.loadDay(today)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWeeklyProposalVisible)
        assertNull(viewModel.uiState.value.tdeeProposal)
    }

    @Test
    fun onDateSelected_updatesSelectedDateAndLoadsData() = runTest {
        val targetDate = LocalDate.of(2026, 11, 20)
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-11-20",
                meal = DefaultMealIds.DINNER,
                productId = testProduct.id,
                productName = testProduct.name,
                grams = 100.0,
                kcal = 370.0,
                protein = 13.5,
                carbs = 60.0,
                fat = 7.0,
                loggedAt = 1000L,
            )
        )

        viewModel.onDateSelected(targetDate)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(targetDate, state.selectedDate)
        assertFalse(state.isToday)
        assertEquals(370.0, state.dayTotals.totals.kcal, 0.001)
    }

    @Test
    fun navigationArrows_operateRelativelyFromJumpedDate() = runTest {
        val targetDate = LocalDate.of(2026, 11, 20)
        viewModel.onDateSelected(targetDate)
        advanceUntilIdle()

        // Step back
        viewModel.onPreviousDay()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 19), viewModel.uiState.value.selectedDate)

        // Step forward twice
        viewModel.onNextDay()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 20), viewModel.uiState.value.selectedDate)

        viewModel.onNextDay()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 21), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun onSelectToday_returnsBackToToday() = runTest {
        val targetDate = LocalDate.of(2026, 11, 20)
        viewModel.onDateSelected(targetDate)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isToday)

        viewModel.onSelectToday()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isToday)
    }

    @Test
    fun loadDay_withConfiguredMeals_ordersSectionsByStartTime() = runTest {
        val today = LocalDate.now()
        viewModel.loadDay(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(4, state.mealSections.size)
        assertEquals(DefaultMealIds.BREAKFAST, state.mealSections[0].mealId)
        assertEquals(DefaultMeals.BREAKFAST_TIME, state.mealSections[0].startTime)
        assertEquals(DefaultMealIds.LUNCH, state.mealSections[1].mealId)
        assertEquals(DefaultMeals.LUNCH_TIME, state.mealSections[1].startTime)
        assertEquals(DefaultMealIds.SNACK, state.mealSections[2].mealId)
        assertEquals(DefaultMeals.SNACK_TIME, state.mealSections[2].startTime)
        assertEquals(DefaultMealIds.DINNER, state.mealSections[3].mealId)
        assertEquals(DefaultMeals.DINNER_TIME, state.mealSections[3].startTime)
    }

    @Test
    fun loadDay_withInactiveMealHavingEntries_showsInactiveMealWithEntries() = runTest {
        val today = LocalDate.now()
        // Deactivate snack in config
        val updated = fakeMealConfigRepository.getMealConfigs().map {
            if (it.id == DefaultMealIds.SNACK) it.copy(isActive = false) else it
        }
        fakeMealConfigRepository.saveMealConfigs(updated)

        // Log an entry for snack today
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = today.toString(),
                meal = DefaultMealIds.SNACK,
                productId = testProduct.id,
                productName = testProduct.name,
                grams = 100.0,
                kcal = 370.0,
                protein = 13.5,
                carbs = 60.0,
                fat = 7.0,
                loggedAt = 1000L,
            )
        )

        viewModel.loadDay(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val snackSection = state.mealSections.find { it.mealId == DefaultMealIds.SNACK }
        assertNotNull(snackSection)
        assertTrue(snackSection!!.isInactive)
        assertEquals(1, snackSection.entries.size)
        assertEquals(370.0, snackSection.totalKcal, 0.001)
    }

    @Test
    fun loadDay_withInactiveMealHavingNoEntries_doesNotShowInactiveMeal() = runTest {
        val today = LocalDate.now()
        // Deactivate snack in config
        val updated = fakeMealConfigRepository.getMealConfigs().map {
            if (it.id == DefaultMealIds.SNACK) it.copy(isActive = false) else it
        }
        fakeMealConfigRepository.saveMealConfigs(updated)

        viewModel.loadDay(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val snackSection = state.mealSections.find { it.mealId == DefaultMealIds.SNACK }
        assertNull(snackSection)
        assertEquals(3, state.mealSections.size)
    }

    @Test
    fun loadDay_withUnknownMealId_showsUnassignedSectionAndCountsInDayTotals() = runTest {
        val today = LocalDate.now()
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = today.toString(),
                meal = "unknown_meal_999",
                productId = testProduct.id,
                productName = testProduct.name,
                grams = 100.0,
                kcal = 370.0,
                protein = 13.5,
                carbs = 60.0,
                fat = 7.0,
                loggedAt = 1000L,
            )
        )

        viewModel.loadDay(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val unassignedSection = state.mealSections.find { it.isUnassigned }
        assertNotNull(unassignedSection)
        assertEquals(DailyPanelViewModel.UNASSIGNED_MEAL_ID, unassignedSection!!.mealId)
        assertEquals(1, unassignedSection.entries.size)
        assertEquals(370.0, unassignedSection.totalKcal, 0.001)

        // Verifies that total day calories count the unassigned entry!
        assertEquals(370.0, state.dayTotals.totals.kcal, 0.001)
    }

    @Test
    fun loadDay_suggestMealPreselectsCurrentSlot() = runTest {
        val today = LocalDate.now()
        viewModel.loadDay(today)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.currentSlotMeal.isNotEmpty())
        assertTrue(state.currentSlotMealName.isNotEmpty())
    }
}

private class FakeDailyLogRepository : DailyLogRepository {
    val logs = mutableMapOf<String, DailyLog>()
    override suspend fun getByDate(date: String): DailyLog? = logs[date]
    override suspend fun insert(log: DailyLog) { logs[log.date] = log }
    override suspend fun setDayType(date: String, dayType: DayType) {
        val existing = logs[date]
        if (existing != null) {
            logs[date] = existing.copy(dayType = dayType)
        } else {
            logs[date] = DailyLog(date = date, dayType = dayType)
        }
    }
    override suspend fun setWaterMl(date: String, waterMl: Int) {}
    override suspend fun setNotes(date: String, notes: String?) {}
}

private class FakeUserSettingsRepository : UserSettingsRepository {
    var pattern = WeeklyDayPattern()
    var minKcal = 1200.0
    var lastReviewedDate: String? = null

    override suspend fun getWeeklyPattern(): WeeklyDayPattern = pattern
    override suspend fun setWeeklyPattern(pattern: WeeklyDayPattern) {
        this.pattern = pattern
    }
    override suspend fun getMinKcalLimit(): Double = minKcal
    override suspend fun setMinKcalLimit(minKcal: Double) {
        this.minKcal = minKcal
    }
    override suspend fun getLastTdeeProposalReviewedDate(): String? = lastReviewedDate
    override suspend fun setLastTdeeProposalReviewedDate(date: String) {
        this.lastReviewedDate = date
    }
}

private class FakeWeightEntryRepository : com.controlcal.core.repo.WeightEntryRepository {
    val entries = mutableListOf<com.controlcal.core.model.WeightEntry>()
    override suspend fun upsert(entry: com.controlcal.core.model.WeightEntry) {
        entries.removeAll { it.date == entry.date }
        entries.add(entry)
    }
    override suspend fun getByDate(date: String): com.controlcal.core.model.WeightEntry? =
        entries.find { it.date == date }
    override suspend fun getBetween(from: String, to: String): List<com.controlcal.core.model.WeightEntry> =
        entries.filter { it.date in from..to }
    override suspend fun getAll(): List<com.controlcal.core.model.WeightEntry> = entries
    override suspend fun deleteByDate(date: String) {
        entries.removeAll { it.date == date }
    }
    override suspend fun moveEntry(oldDate: String, newEntry: com.controlcal.core.model.WeightEntry) {
        if (oldDate != newEntry.date) {
            deleteByDate(oldDate)
        }
        upsert(newEntry)
    }
}

private class FakeGoalProfileRepository : GoalProfileRepository {
    val profiles = mutableListOf<GoalProfile>()
    override suspend fun insert(profile: GoalProfile): Long {
        val id = (profiles.size + 1).toLong()
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

    override suspend fun getLatestDateWithMealBefore(date: String, meal: String): String? =
        entries.filter { it.date < date && it.meal == meal }.maxOfOrNull { it.date }

    override suspend fun countByMeal(meal: String): Int = entries.count { it.meal == meal }
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
