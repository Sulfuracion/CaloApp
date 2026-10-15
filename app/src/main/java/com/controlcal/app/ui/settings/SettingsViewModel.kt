package com.controlcal.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.core.meal.AddMealConfigUseCase
import com.controlcal.core.meal.ChangeMealStartTimeUseCase
import com.controlcal.core.meal.DeactivateMealUseCase
import com.controlcal.core.meal.DeleteMealConfigUseCase
import com.controlcal.core.meal.MealConfigResult
import com.controlcal.core.meal.ReactivateMealUseCase
import com.controlcal.core.meal.RenameMealConfigUseCase
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.nutrition.GetActiveGoalProfileUseCase
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealConfigRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    private val goalProfileRepository: GoalProfileRepository,
    private val getActiveGoalProfileUseCase: GetActiveGoalProfileUseCase,
    private val mealConfigRepository: MealConfigRepository,
    private val mealEntryRepository: MealEntryRepository,
) : ViewModel() {

    private val addMealConfigUseCase = AddMealConfigUseCase()
    private val renameMealConfigUseCase = RenameMealConfigUseCase()
    private val changeMealStartTimeUseCase = ChangeMealStartTimeUseCase()
    private val deactivateMealUseCase = DeactivateMealUseCase()
    private val reactivateMealUseCase = ReactivateMealUseCase()
    private val deleteMealConfigUseCase = DeleteMealConfigUseCase()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val todayStr = LocalDate.now().toString()
            val pattern = userSettingsRepository.getWeeklyPattern()
            val minKcal = userSettingsRepository.getMinKcalLimit()
            val trainingProfile = getActiveGoalProfileUseCase(DayType.TRAINING, todayStr)
            val restProfile = getActiveGoalProfileUseCase(DayType.REST, todayStr)
            val meals = mealConfigRepository.getMealConfigs().sortedBy { it.startTime }

            _uiState.update {
                it.copy(
                    weeklyPattern = pattern,
                    minKcalText = NumberFormatHelper.formatDecimal(minKcal),
                    trainingKcalText = NumberFormatHelper.formatDecimal(trainingProfile.kcal),
                    trainingProteinText = NumberFormatHelper.formatDecimal(trainingProfile.proteinG),
                    trainingCarbsText = NumberFormatHelper.formatDecimal(trainingProfile.carbsG),
                    trainingFatText = NumberFormatHelper.formatDecimal(trainingProfile.fatG),
                    restKcalText = NumberFormatHelper.formatDecimal(restProfile.kcal),
                    restProteinText = NumberFormatHelper.formatDecimal(restProfile.proteinG),
                    restCarbsText = NumberFormatHelper.formatDecimal(restProfile.carbsG),
                    restFatText = NumberFormatHelper.formatDecimal(restProfile.fatG),
                    configuredMeals = meals,
                    isLoading = false,
                )
            }
        }
    }

    fun onDayPatternToggled(dayOfWeek: DayOfWeek, dayType: DayType) {
        viewModelScope.launch {
            val updated = _uiState.value.weeklyPattern.withDayType(dayOfWeek, dayType)
            userSettingsRepository.setWeeklyPattern(updated)
            _uiState.update { it.copy(weeklyPattern = updated) }
        }
    }

    fun onTrainingKcalChanged(value: String) {
        _uiState.update { it.copy(trainingKcalText = value, errorMessage = null) }
    }

    fun onTrainingProteinChanged(value: String) {
        _uiState.update { it.copy(trainingProteinText = value, errorMessage = null) }
    }

    fun onTrainingCarbsChanged(value: String) {
        _uiState.update { it.copy(trainingCarbsText = value, errorMessage = null) }
    }

    fun onTrainingFatChanged(value: String) {
        _uiState.update { it.copy(trainingFatText = value, errorMessage = null) }
    }

    fun onRestKcalChanged(value: String) {
        _uiState.update { it.copy(restKcalText = value, errorMessage = null) }
    }

    fun onRestProteinChanged(value: String) {
        _uiState.update { it.copy(restProteinText = value, errorMessage = null) }
    }

    fun onRestCarbsChanged(value: String) {
        _uiState.update { it.copy(restCarbsText = value, errorMessage = null) }
    }

    fun onRestFatChanged(value: String) {
        _uiState.update { it.copy(restFatText = value, errorMessage = null) }
    }

    fun onMinKcalChanged(value: String) {
        _uiState.update { it.copy(minKcalText = value, errorMessage = null) }
    }

    fun onSaveGoals() {
        val state = _uiState.value
        val minKcal = NumberFormatHelper.parseDecimal(state.minKcalText)
        val tKcal = NumberFormatHelper.parseDecimal(state.trainingKcalText)
        val tProtein = NumberFormatHelper.parseDecimal(state.trainingProteinText)
        val tCarbs = NumberFormatHelper.parseDecimal(state.trainingCarbsText)
        val tFat = NumberFormatHelper.parseDecimal(state.trainingFatText)

        val rKcal = NumberFormatHelper.parseDecimal(state.restKcalText)
        val rProtein = NumberFormatHelper.parseDecimal(state.restProteinText)
        val rCarbs = NumberFormatHelper.parseDecimal(state.restCarbsText)
        val rFat = NumberFormatHelper.parseDecimal(state.restFatText)

        if (minKcal == null || minKcal <= 0 ||
            tKcal == null || tKcal <= 0 ||
            tProtein == null || tProtein < 0 ||
            tCarbs == null || tCarbs < 0 ||
            tFat == null || tFat < 0 ||
            rKcal == null || rKcal <= 0 ||
            rProtein == null || rProtein < 0 ||
            rCarbs == null || rCarbs < 0 ||
            rFat == null || rFat < 0
        ) {
            _uiState.update { it.copy(errorMessage = "Comprueba que todos los valores sean números válidos") }
            return
        }

        viewModelScope.launch {
            userSettingsRepository.setMinKcalLimit(minKcal)
            val todayStr = LocalDate.now().toString()
            val newTrainingProfile = GoalProfile(
                dayType = DayType.TRAINING,
                kcal = tKcal,
                proteinG = tProtein,
                carbsG = tCarbs,
                fatG = tFat,
                validFrom = todayStr,
                origin = GoalOrigin.MANUAL,
            )
            val newRestProfile = GoalProfile(
                dayType = DayType.REST,
                kcal = rKcal,
                proteinG = rProtein,
                carbsG = rCarbs,
                fatG = rFat,
                validFrom = todayStr,
                origin = GoalOrigin.MANUAL,
            )

            goalProfileRepository.insert(newTrainingProfile)
            goalProfileRepository.insert(newRestProfile)

            _uiState.update {
                it.copy(
                    infoMessage = "Objetivos guardados para hoy y días posteriores",
                    errorMessage = null,
                )
            }
        }
    }

    fun onOpenAddMealDialog() {
        _uiState.update { it.copy(showAddMealDialog = true, errorMessage = null) }
    }

    fun onDismissAddMealDialog() {
        _uiState.update { it.copy(showAddMealDialog = false) }
    }

    fun onConfirmAddMeal(name: String, startTime: LocalTime) {
        viewModelScope.launch {
            val currentMeals = _uiState.value.configuredMeals
            when (val result = addMealConfigUseCase(currentMeals, name, startTime, isActive = true)) {
                is MealConfigResult.Success -> {
                    mealConfigRepository.saveMealConfigs(result.meals)
                    val sorted = result.meals.sortedBy { it.startTime }
                    _uiState.update {
                        it.copy(
                            configuredMeals = sorted,
                            showAddMealDialog = false,
                            infoMessage = "Comida «$name» añadida",
                            errorMessage = null,
                        )
                    }
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun onOpenRenameMealDialog(meal: ConfiguredMeal) {
        _uiState.update { it.copy(editingMeal = meal, errorMessage = null) }
    }

    fun onDismissRenameMealDialog() {
        _uiState.update { it.copy(editingMeal = null) }
    }

    fun onConfirmRenameMeal(mealId: String, newName: String) {
        viewModelScope.launch {
            val currentMeals = _uiState.value.configuredMeals
            when (val result = renameMealConfigUseCase(currentMeals, mealId, newName)) {
                is MealConfigResult.Success -> {
                    mealConfigRepository.saveMealConfigs(result.meals)
                    val sorted = result.meals.sortedBy { it.startTime }
                    _uiState.update {
                        it.copy(
                            configuredMeals = sorted,
                            editingMeal = null,
                            infoMessage = "Comida renombrada a «$newName»",
                            errorMessage = null,
                        )
                    }
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun onOpenChangeTimeDialog(meal: ConfiguredMeal) {
        _uiState.update { it.copy(changingTimeMeal = meal, errorMessage = null) }
    }

    fun onDismissChangeTimeDialog() {
        _uiState.update { it.copy(changingTimeMeal = null) }
    }

    fun onConfirmChangeStartTime(mealId: String, newStartTime: LocalTime) {
        viewModelScope.launch {
            val currentMeals = _uiState.value.configuredMeals
            val meal = currentMeals.find { it.id == mealId }
            val mealName = meal?.name ?: ""
            when (val result = changeMealStartTimeUseCase(currentMeals, mealId, newStartTime)) {
                is MealConfigResult.Success -> {
                    mealConfigRepository.saveMealConfigs(result.meals)
                    val sorted = result.meals.sortedBy { it.startTime }
                    _uiState.update {
                        it.copy(
                            configuredMeals = sorted,
                            changingTimeMeal = null,
                            infoMessage = "Hora de «$mealName» actualizada",
                            errorMessage = null,
                        )
                    }
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun onDeactivateMeal(mealId: String) {
        viewModelScope.launch {
            val currentMeals = _uiState.value.configuredMeals
            val meal = currentMeals.find { it.id == mealId }
            val mealName = meal?.name ?: ""
            when (val result = deactivateMealUseCase(currentMeals, mealId)) {
                is MealConfigResult.Success -> {
                    mealConfigRepository.saveMealConfigs(result.meals)
                    val sorted = result.meals.sortedBy { it.startTime }
                    _uiState.update {
                        it.copy(
                            configuredMeals = sorted,
                            deactivateOfferMeal = null,
                            infoMessage = "Comida «$mealName» desactivada",
                            errorMessage = null,
                        )
                    }
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun onReactivateMeal(mealId: String) {
        viewModelScope.launch {
            val currentMeals = _uiState.value.configuredMeals
            val meal = currentMeals.find { it.id == mealId }
            val mealName = meal?.name ?: ""
            when (val result = reactivateMealUseCase(currentMeals, mealId)) {
                is MealConfigResult.Success -> {
                    mealConfigRepository.saveMealConfigs(result.meals)
                    val sorted = result.meals.sortedBy { it.startTime }
                    _uiState.update {
                        it.copy(
                            configuredMeals = sorted,
                            infoMessage = "Comida «$mealName» reactivada",
                            errorMessage = null,
                        )
                    }
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun onDeleteMealRequested(meal: ConfiguredMeal) {
        viewModelScope.launch {
            val entriesCount = mealEntryRepository.countByMeal(meal.id)
            if (entriesCount > 0) {
                _uiState.update { it.copy(deactivateOfferMeal = meal, errorMessage = null) }
            } else {
                _uiState.update { it.copy(deleteConfirmMeal = meal, errorMessage = null) }
            }
        }
    }

    fun onConfirmDeleteMeal(mealId: String) {
        viewModelScope.launch {
            val currentMeals = _uiState.value.configuredMeals
            val meal = currentMeals.find { it.id == mealId }
            val mealName = meal?.name ?: ""
            val entriesCount = mealEntryRepository.countByMeal(mealId)
            when (val result = deleteMealConfigUseCase(currentMeals, mealId, hasEntries = entriesCount > 0)) {
                is MealConfigResult.Success -> {
                    mealConfigRepository.saveMealConfigs(result.meals)
                    val sorted = result.meals.sortedBy { it.startTime }
                    _uiState.update {
                        it.copy(
                            configuredMeals = sorted,
                            deleteConfirmMeal = null,
                            infoMessage = "Comida «$mealName» eliminada",
                            errorMessage = null,
                        )
                    }
                }
                is MealConfigResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            deleteConfirmMeal = null,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun onDismissDeleteDialogs() {
        _uiState.update { it.copy(deactivateOfferMeal = null, deleteConfirmMeal = null) }
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}
