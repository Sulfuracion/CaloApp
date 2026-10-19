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
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.nutrition.GoalProfileValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

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
            val dayName = when (dayOfWeek) {
                DayOfWeek.MONDAY -> "Lunes"
                DayOfWeek.TUESDAY -> "Martes"
                DayOfWeek.WEDNESDAY -> "Miércoles"
                DayOfWeek.THURSDAY -> "Jueves"
                DayOfWeek.FRIDAY -> "Viernes"
                DayOfWeek.SATURDAY -> "Sábado"
                DayOfWeek.SUNDAY -> "Domingo"
            }
            val typeName = if (dayType == DayType.TRAINING) "Entrenamiento" else "Descanso"
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.feedback_weekly_pattern_saved, dayName, typeName)
                )
            )
        }
    }

    fun onTrainingKcalChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateKcal(value) -> "Debe ser mayor que 0"
            else -> null
        }
        _uiState.update { it.copy(trainingKcalText = value, trainingKcalError = error, errorMessage = null) }
    }

    fun onTrainingProteinChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateMacro(value) -> "No puede ser negativo"
            else -> null
        }
        _uiState.update { it.copy(trainingProteinText = value, trainingProteinError = error, errorMessage = null) }
    }

    fun onTrainingCarbsChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateMacro(value) -> "No puede ser negativo"
            else -> null
        }
        _uiState.update { it.copy(trainingCarbsText = value, trainingCarbsError = error, errorMessage = null) }
    }

    fun onTrainingFatChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateMacro(value) -> "No puede ser negativo"
            else -> null
        }
        _uiState.update { it.copy(trainingFatText = value, trainingFatError = error, errorMessage = null) }
    }

    fun onRestKcalChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateKcal(value) -> "Debe ser mayor que 0"
            else -> null
        }
        _uiState.update { it.copy(restKcalText = value, restKcalError = error, errorMessage = null) }
    }

    fun onRestProteinChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateMacro(value) -> "No puede ser negativo"
            else -> null
        }
        _uiState.update { it.copy(restProteinText = value, restProteinError = error, errorMessage = null) }
    }

    fun onRestCarbsChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateMacro(value) -> "No puede ser negativo"
            else -> null
        }
        _uiState.update { it.copy(restCarbsText = value, restCarbsError = error, errorMessage = null) }
    }

    fun onRestFatChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateMacro(value) -> "No puede ser negativo"
            else -> null
        }
        _uiState.update { it.copy(restFatText = value, restFatError = error, errorMessage = null) }
    }

    fun onMinKcalChanged(value: String) {
        val error = when {
            value.isBlank() -> "Campo obligatorio"
            !GoalProfileValidator.validateMinKcal(value) -> "Debe ser mayor que 0"
            else -> null
        }
        _uiState.update { it.copy(minKcalText = value, minKcalError = error, errorMessage = null) }
    }

    fun onSaveGoals() {
        val state = _uiState.value
        val minKcalValid = GoalProfileValidator.validateMinKcal(state.minKcalText)
        val tKcalValid = GoalProfileValidator.validateKcal(state.trainingKcalText)
        val tProteinValid = GoalProfileValidator.validateMacro(state.trainingProteinText)
        val tCarbsValid = GoalProfileValidator.validateMacro(state.trainingCarbsText)
        val tFatValid = GoalProfileValidator.validateMacro(state.trainingFatText)
        val rKcalValid = GoalProfileValidator.validateKcal(state.restKcalText)
        val rProteinValid = GoalProfileValidator.validateMacro(state.restProteinText)
        val rCarbsValid = GoalProfileValidator.validateMacro(state.restCarbsText)
        val rFatValid = GoalProfileValidator.validateMacro(state.restFatText)

        if (!minKcalValid || !tKcalValid || !tProteinValid || !tCarbsValid || !tFatValid ||
            !rKcalValid || !rProteinValid || !rCarbsValid || !rFatValid
        ) {
            _uiState.update {
                it.copy(
                    minKcalError = if (!minKcalValid) "Introduce un valor mayor que 0" else null,
                    trainingKcalError = if (!tKcalValid) "Introduce un valor mayor que 0" else null,
                    trainingProteinError = if (!tProteinValid) "Introduce un valor no negativo" else null,
                    trainingCarbsError = if (!tCarbsValid) "Introduce un valor no negativo" else null,
                    trainingFatError = if (!tFatValid) "Introduce un valor no negativo" else null,
                    restKcalError = if (!rKcalValid) "Introduce un valor mayor que 0" else null,
                    restProteinError = if (!rProteinValid) "Introduce un valor no negativo" else null,
                    restCarbsError = if (!rCarbsValid) "Introduce un valor no negativo" else null,
                    restFatError = if (!rFatValid) "Introduce un valor no negativo" else null,
                    errorMessage = "Comprueba que todos los valores sean números válidos",
                )
            }
            return
        }

        val minKcal = NumberFormatHelper.parseDecimal(state.minKcalText) ?: return
        val tKcal = NumberFormatHelper.parseDecimal(state.trainingKcalText) ?: return
        val tProtein = NumberFormatHelper.parseDecimal(state.trainingProteinText) ?: return
        val tCarbs = NumberFormatHelper.parseDecimal(state.trainingCarbsText) ?: return
        val tFat = NumberFormatHelper.parseDecimal(state.trainingFatText) ?: return
        val rKcal = NumberFormatHelper.parseDecimal(state.restKcalText) ?: return
        val rProtein = NumberFormatHelper.parseDecimal(state.restProteinText) ?: return
        val rCarbs = NumberFormatHelper.parseDecimal(state.restCarbsText) ?: return
        val rFat = NumberFormatHelper.parseDecimal(state.restFatText) ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingGoals = true) }
            try {
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
                        isSavingGoals = false,
                        infoMessage = "Objetivos guardados para hoy y días posteriores",
                        errorMessage = null,
                    )
                }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_goals_saved)))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingGoals = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.feedback_save_error)))
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
            _uiState.update { it.copy(isSavingMeal = true) }
            val currentMeals = _uiState.value.configuredMeals
            when (val result = addMealConfigUseCase(currentMeals, name, startTime, isActive = true)) {
                is MealConfigResult.Success -> {
                    mealConfigRepository.saveMealConfigs(result.meals)
                    val sorted = result.meals.sortedBy { it.startTime }
                    _uiState.update {
                        it.copy(
                            configuredMeals = sorted,
                            showAddMealDialog = false,
                            isSavingMeal = false,
                            infoMessage = "Comida «$name» añadida",
                            errorMessage = null,
                        )
                    }
                    _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_meal_added, name)))
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(isSavingMeal = false, errorMessage = result.message) }
                    _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString(result.message)))
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
            _uiState.update { it.copy(isSavingMeal = true) }
            val currentMeals = _uiState.value.configuredMeals
            when (val result = renameMealConfigUseCase(currentMeals, mealId, newName)) {
                is MealConfigResult.Success -> {
                    mealConfigRepository.saveMealConfigs(result.meals)
                    val sorted = result.meals.sortedBy { it.startTime }
                    _uiState.update {
                        it.copy(
                            configuredMeals = sorted,
                            editingMeal = null,
                            isSavingMeal = false,
                            infoMessage = "Comida renombrada a «$newName»",
                            errorMessage = null,
                        )
                    }
                    _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_meal_renamed, newName)))
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(isSavingMeal = false, errorMessage = result.message) }
                    _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString(result.message)))
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
            _uiState.update { it.copy(isSavingMeal = true) }
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
                            isSavingMeal = false,
                            infoMessage = "Hora de «$mealName» actualizada",
                            errorMessage = null,
                        )
                    }
                    val formattedTime = String.format("%02d:%02d", newStartTime.hour, newStartTime.minute)
                    _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_meal_time_updated, formattedTime)))
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(isSavingMeal = false, errorMessage = result.message) }
                    _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString(result.message)))
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
                    _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_meal_deactivated, mealName)))
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                    _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString(result.message)))
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
                    _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_meal_reactivated, mealName)))
                }
                is MealConfigResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                    _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString(result.message)))
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
                    _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_meal_deleted, mealName)))
                }
                is MealConfigResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            deleteConfirmMeal = null,
                            errorMessage = result.message,
                        )
                    }
                    _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString(result.message)))
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
