package com.controlcal.app.ui.settings

import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DayType
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.DefaultMeals
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.nutrition.GetActiveGoalProfileUseCase
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealConfigRepository
import com.controlcal.core.repo.MealEntryRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeUserSettingsRepository: FakeUserSettingsRepository
    private lateinit var fakeGoalProfileRepository: FakeGoalProfileRepository
    private lateinit var fakeMealConfigRepository: FakeMealConfigRepository
    private lateinit var fakeMealEntryRepository: FakeMealEntryRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeUserSettingsRepository = FakeUserSettingsRepository()
        fakeGoalProfileRepository = FakeGoalProfileRepository()
        fakeMealConfigRepository = FakeMealConfigRepository()
        fakeMealEntryRepository = FakeMealEntryRepository()

        // Insert initial profiles with validFrom in the past
        fakeGoalProfileRepository.profiles.add(
            GoalProfile(
                id = 1L,
                dayType = DayType.TRAINING,
                kcal = 2400.0,
                proteinG = 160.0,
                carbsG = 270.0,
                fatG = 75.0,
                validFrom = "2026-01-01",
                origin = GoalOrigin.MANUAL,
            )
        )
        fakeGoalProfileRepository.profiles.add(
            GoalProfile(
                id = 2L,
                dayType = DayType.REST,
                kcal = 2000.0,
                proteinG = 160.0,
                carbsG = 180.0,
                fatG = 70.0,
                validFrom = "2026-01-01",
                origin = GoalOrigin.MANUAL,
            )
        )

        val getActiveGoalProfileUseCase = GetActiveGoalProfileUseCase(fakeGoalProfileRepository)
        viewModel = SettingsViewModel(
            userSettingsRepository = fakeUserSettingsRepository,
            goalProfileRepository = fakeGoalProfileRepository,
            getActiveGoalProfileUseCase = getActiveGoalProfileUseCase,
            mealConfigRepository = fakeMealConfigRepository,
            mealEntryRepository = fakeMealEntryRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsWeeklyPatternAndActiveGoalProfiles() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DayType.TRAINING, state.weeklyPattern.dayTypeFor(DayOfWeek.MONDAY))
        assertEquals(DayType.REST, state.weeklyPattern.dayTypeFor(DayOfWeek.TUESDAY))

        assertEquals("2400", state.trainingKcalText)
        assertEquals("160", state.trainingProteinText)
        assertEquals("2000", state.restKcalText)
        assertEquals("180", state.restCarbsText)
    }

    @Test
    fun onDayPatternToggled_updatesPatternInSettingsAndUi() = runTest {
        advanceUntilIdle()

        viewModel.onDayPatternToggled(DayOfWeek.TUESDAY, DayType.TRAINING)
        advanceUntilIdle()

        assertEquals(DayType.TRAINING, viewModel.uiState.value.weeklyPattern.dayTypeFor(DayOfWeek.TUESDAY))
        assertEquals(DayType.TRAINING, fakeUserSettingsRepository.pattern.dayTypeFor(DayOfWeek.TUESDAY))
    }

    @Test
    fun onSaveGoals_withValidInputs_createsProfilesForTodayWithoutAlteringPastDays() = runTest {
        advanceUntilIdle()

        // Change training kcal to 2600 and rest kcal to 2100
        viewModel.onTrainingKcalChanged("2600")
        viewModel.onTrainingProteinChanged("175")
        viewModel.onTrainingCarbsChanged("290")
        viewModel.onTrainingFatChanged("80")

        viewModel.onRestKcalChanged("2100")
        viewModel.onRestProteinChanged("170")
        viewModel.onRestCarbsChanged("190")
        viewModel.onRestFatChanged("72")

        viewModel.onSaveGoals()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.infoMessage)
        assertNull(viewModel.uiState.value.errorMessage)

        val todayStr = LocalDate.now().toString()
        val yesterdayStr = LocalDate.now().minusDays(1).toString()

        // Past day query: must return the original profiles (2400 kcal and 2000 kcal)
        val pastTraining = fakeGoalProfileRepository.getProfileForDayType(DayType.TRAINING, yesterdayStr)
        assertNotNull(pastTraining)
        assertEquals(2400.0, pastTraining!!.kcal, 0.001)

        val pastRest = fakeGoalProfileRepository.getProfileForDayType(DayType.REST, yesterdayStr)
        assertNotNull(pastRest)
        assertEquals(2000.0, pastRest!!.kcal, 0.001)

        // Today query: must return the new profiles (2600 kcal and 2100 kcal)
        val todayTraining = fakeGoalProfileRepository.getProfileForDayType(DayType.TRAINING, todayStr)
        assertNotNull(todayTraining)
        assertEquals(2600.0, todayTraining!!.kcal, 0.001)
        assertEquals(175.0, todayTraining.proteinG, 0.001)

        val todayRest = fakeGoalProfileRepository.getProfileForDayType(DayType.REST, todayStr)
        assertNotNull(todayRest)
        assertEquals(2100.0, todayRest!!.kcal, 0.001)
        assertEquals(170.0, todayRest.proteinG, 0.001)
    }

    @Test
    fun onSaveGoals_withInvalidInputs_displaysErrorMessage() = runTest {
        advanceUntilIdle()

        viewModel.onTrainingKcalChanged("abc")
        viewModel.onSaveGoals()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onSaveGoals_savesMinKcalLimit() = runTest {
        advanceUntilIdle()

        viewModel.onMinKcalChanged("1400")
        viewModel.onSaveGoals()
        advanceUntilIdle()

        assertEquals(1400.0, fakeUserSettingsRepository.minKcal, 0.001)
    }

    @Test
    fun initialState_loadsConfiguredMeals() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(4, state.configuredMeals.size)
        assertEquals(DefaultMealIds.BREAKFAST, state.configuredMeals[0].id)
        assertEquals(DefaultMealIds.LUNCH, state.configuredMeals[1].id)
    }

    @Test
    fun onAddMeal_addsMealToRepositoryAndUpdatesUi() = runTest {
        advanceUntilIdle()

        viewModel.onConfirmAddMeal("Post-entreno", LocalTime.of(18, 0))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.configuredMeals.size)
        assertTrue(state.configuredMeals.any { it.name == "Post-entreno" && it.startTime == LocalTime.of(18, 0) })
    }

    @Test
    fun onRenameMeal_renamesConfiguredMeal() = runTest {
        advanceUntilIdle()

        val breakfast = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.BREAKFAST }
        viewModel.onConfirmRenameMeal(breakfast.id, "Desayuno Fuerte")
        advanceUntilIdle()

        val updated = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.BREAKFAST }
        assertEquals("Desayuno Fuerte", updated.name)
    }

    @Test
    fun onChangeMealStartTime_updatesStartTime() = runTest {
        advanceUntilIdle()

        val breakfast = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.BREAKFAST }
        viewModel.onConfirmChangeStartTime(breakfast.id, LocalTime.of(8, 15))
        advanceUntilIdle()

        val updated = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.BREAKFAST }
        assertEquals(LocalTime.of(8, 15), updated.startTime)
    }

    @Test
    fun onDeactivateAndReactivateMeal_togglesActiveState() = runTest {
        advanceUntilIdle()

        val snack = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.SNACK }
        viewModel.onDeactivateMeal(snack.id)
        advanceUntilIdle()

        val deactivated = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.SNACK }
        assertFalse(deactivated.isActive)

        viewModel.onReactivateMeal(deactivated.id)
        advanceUntilIdle()

        val reactivated = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.SNACK }
        assertTrue(reactivated.isActive)
    }

    @Test
    fun onRequestDeleteMeal_withNoEntries_showsDeleteConfirmDialog_andDeletesOnConfirm() = runTest {
        advanceUntilIdle()

        val snack = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.SNACK }
        viewModel.onDeleteMealRequested(snack)
        advanceUntilIdle()

        assertEquals(snack, viewModel.uiState.value.deleteConfirmMeal)
        assertNull(viewModel.uiState.value.deactivateOfferMeal)

        viewModel.onConfirmDeleteMeal(snack.id)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteConfirmMeal)
        assertFalse(viewModel.uiState.value.configuredMeals.any { it.id == DefaultMealIds.SNACK })
    }

    @Test
    fun onRequestDeleteMeal_withEntries_showsDeactivateOfferDialog_andDeactivatesOnAccept() = runTest {
        advanceUntilIdle()

        val snack = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.SNACK }
        fakeMealEntryRepository.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-14",
                meal = DefaultMealIds.SNACK,
                productId = 1L,
                productName = "Manzana",
                grams = 150.0,
                kcal = 78.0,
                protein = 0.4,
                carbs = 20.0,
                fat = 0.2,
                loggedAt = 1000L,
            )
        )

        viewModel.onDeleteMealRequested(snack)
        advanceUntilIdle()

        assertEquals(snack, viewModel.uiState.value.deactivateOfferMeal)
        assertNull(viewModel.uiState.value.deleteConfirmMeal)

        viewModel.onDeactivateMeal(snack.id)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deactivateOfferMeal)
        val updatedSnack = viewModel.uiState.value.configuredMeals.first { it.id == DefaultMealIds.SNACK }
        assertFalse(updatedSnack.isActive)
    }

    @Test
    fun healthConnect_toggleOn_showsRationaleDialogWhenSupported() = runTest {
        val fakeHc = object : com.controlcal.app.data.health.HealthConnectManager {
            override fun isSupported(): Boolean = true
            override suspend fun hasStepsPermission(): Boolean = false
            override fun getPermissions(): Set<String> = setOf("android.permission.health.READ_STEPS")
            override suspend fun readStepsForDate(date: LocalDate): Long? = null
            override suspend fun readYesterdaySteps(): Long? = null
        }
        val vm = SettingsViewModel(
            userSettingsRepository = fakeUserSettingsRepository,
            goalProfileRepository = fakeGoalProfileRepository,
            getActiveGoalProfileUseCase = GetActiveGoalProfileUseCase(fakeGoalProfileRepository),
            mealConfigRepository = fakeMealConfigRepository,
            mealEntryRepository = fakeMealEntryRepository,
            healthConnectManager = fakeHc,
        )
        advanceUntilIdle()

        vm.onToggleHealthConnectSteps(true)
        assertTrue(vm.uiState.value.showHealthConnectRationaleDialog)

        vm.onDismissHealthConnectRationale()
        assertFalse(vm.uiState.value.showHealthConnectRationaleDialog)
    }

    @Test
    fun healthConnect_permissionResultGranted_enablesSync() = runTest {
        val fakeHc = object : com.controlcal.app.data.health.HealthConnectManager {
            override fun isSupported(): Boolean = true
            override suspend fun hasStepsPermission(): Boolean = true
            override fun getPermissions(): Set<String> = setOf("android.permission.health.READ_STEPS")
            override suspend fun readStepsForDate(date: LocalDate): Long? = 7500L
            override suspend fun readYesterdaySteps(): Long? = 7500L
        }
        val vm = SettingsViewModel(
            userSettingsRepository = fakeUserSettingsRepository,
            goalProfileRepository = fakeGoalProfileRepository,
            getActiveGoalProfileUseCase = GetActiveGoalProfileUseCase(fakeGoalProfileRepository),
            mealConfigRepository = fakeMealConfigRepository,
            mealEntryRepository = fakeMealEntryRepository,
            healthConnectManager = fakeHc,
        )
        advanceUntilIdle()

        vm.onHealthConnectPermissionsResult(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isHealthConnectStepsSyncEnabled)
        assertTrue(fakeUserSettingsRepository.isHealthConnectStepsSyncEnabled())

        vm.onToggleHealthConnectSteps(false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isHealthConnectStepsSyncEnabled)
        assertFalse(fakeUserSettingsRepository.isHealthConnectStepsSyncEnabled())
    }

    @Test
    fun onExportClicked_opensDialogAndClearsPasswords() = runTest {
        viewModel.onExportClicked()
        val state = viewModel.uiState.value
        assertTrue(state.isExportPasswordDialogOpen)
        assertEquals("", state.exportPasswordText)
        assertEquals("", state.exportConfirmPasswordText)
        assertNull(state.exportPasswordError)
    }

    @Test
    fun onConfirmExportPassword_validatesMinimumLengthAndMatching() = runTest {
        viewModel.onExportClicked()

        // Too short
        viewModel.onExportPasswordChanged("short")
        viewModel.onExportConfirmPasswordChanged("short")
        var callbackCalled = false
        viewModel.onConfirmExportPassword { callbackCalled = true }
        assertFalse(callbackCalled)
        assertNotNull(viewModel.uiState.value.exportPasswordError)

        // Mismatched
        viewModel.onExportPasswordChanged("password123")
        viewModel.onExportConfirmPasswordChanged("password456")
        viewModel.onConfirmExportPassword { callbackCalled = true }
        assertFalse(callbackCalled)
        assertNotNull(viewModel.uiState.value.exportPasswordError)

        // Valid
        viewModel.onExportPasswordChanged("validPassword123")
        viewModel.onExportConfirmPasswordChanged("validPassword123")
        viewModel.onConfirmExportPassword { callbackCalled = true }
        assertTrue(callbackCalled)
        assertFalse(viewModel.uiState.value.isExportPasswordDialogOpen)
        assertNull(viewModel.uiState.value.exportPasswordError)
    }

    @Test
    fun onDismissBackupDialogs_resetsAllDialogStates() = runTest {
        viewModel.onExportClicked()
        assertTrue(viewModel.uiState.value.isExportPasswordDialogOpen)

        viewModel.onDismissBackupDialogs()
        assertFalse(viewModel.uiState.value.isExportPasswordDialogOpen)
        assertFalse(viewModel.uiState.value.isImportPasswordDialogOpen)
        assertFalse(viewModel.uiState.value.isRestoreConfirmDialogOpen)
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

    private class FakeMealEntryRepository : MealEntryRepository {
        val entries = mutableListOf<MealEntry>()

        override suspend fun insert(entry: MealEntry): Long {
            val id = (entries.size + 1).toLong()
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
        override suspend fun delete(entry: MealEntry) {}
        override suspend fun getByDate(date: String): List<MealEntry> = entries.filter { it.date == date }
        override suspend fun getByDateAndMeal(date: String, meal: String): List<MealEntry> = entries.filter { it.date == date && it.meal == meal }
        override suspend fun getByProduct(productId: Long): List<MealEntry> = entries.filter { it.productId == productId }
        override suspend fun getSince(sinceMillis: Long): List<MealEntry> = entries.filter { it.loggedAt >= sinceMillis }
        override suspend fun getLatestDateWithMealBefore(date: String, meal: String): String? = null
        override suspend fun countByMeal(meal: String): Int = entries.count { it.meal == meal }
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

        var stepsSyncEnabled = false
        override suspend fun isHealthConnectStepsSyncEnabled(): Boolean = stepsSyncEnabled
        override suspend fun setHealthConnectStepsSyncEnabled(enabled: Boolean) {
            stepsSyncEnabled = enabled
        }

        var lastBackup: Long? = null
        override suspend fun getLastBackupTimestamp(): Long? = lastBackup
        override suspend fun setLastBackupTimestamp(timestamp: Long) {
            this.lastBackup = timestamp
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
}
