package com.controlcal.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.R
import com.controlcal.app.data.backup.BackupManager
import com.controlcal.app.data.health.HealthConnectManager
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.backup.BackupCodec
import com.controlcal.core.backup.BackupPayload
import com.controlcal.core.backup.InvalidPasswordOrCorruptException
import com.controlcal.core.backup.NewerSchemaVersionException
import com.controlcal.core.meal.AddMealConfigUseCase
import com.controlcal.core.meal.ChangeMealStartTimeUseCase
import com.controlcal.core.meal.DeactivateMealUseCase
import com.controlcal.core.meal.DeleteMealConfigUseCase
import com.controlcal.core.meal.MealConfigResult
import com.controlcal.core.meal.ReactivateMealUseCase
import com.controlcal.core.meal.RenameMealConfigUseCase
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.WeightEntry
import com.controlcal.core.nutrition.GetActiveGoalProfileUseCase
import com.controlcal.core.nutrition.GoalProfileValidator
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.profile.UserProfile
import com.controlcal.core.profile.UserProfileIssue
import com.controlcal.core.profile.UserProfileValidationResult
import com.controlcal.core.profile.UserProfileValidator
import com.controlcal.core.profile.UserSex
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealConfigRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.UserSettingsRepository
import com.controlcal.core.repo.WeightEntryRepository
import com.controlcal.core.tdee.WeightTrend
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
    private val backupManager: BackupManager? = null,
    private val healthConnectManager: HealthConnectManager = object : HealthConnectManager {
        override fun isSupported(): Boolean = false
        override suspend fun hasStepsPermission(): Boolean = false
        override fun getPermissions(): Set<String> = emptySet()
        override suspend fun readStepsForDate(date: LocalDate): Long? = null
        override suspend fun readYesterdaySteps(): Long? = null
    },
    private val dailyLogRepository: DailyLogRepository = object : DailyLogRepository {
        override suspend fun getByDate(date: String): DailyLog? = null
        override suspend fun insert(log: DailyLog) {}
        override suspend fun setDayType(date: String, dayType: DayType) {}
        override suspend fun setWaterMl(date: String, waterMl: Int) {}
        override suspend fun setSteps(date: String, steps: Int) {}
        override suspend fun setNotes(date: String, notes: String?) {}
    },
    private val weightEntryRepository: WeightEntryRepository = object : WeightEntryRepository {
        override suspend fun upsert(entry: WeightEntry) {}
        override suspend fun getByDate(date: String): WeightEntry? = null
        override suspend fun getBetween(from: String, to: String): List<WeightEntry> = emptyList()
        override suspend fun getAll(): List<WeightEntry> = emptyList()
        override suspend fun deleteByDate(date: String) {}
        override suspend fun moveEntry(oldDate: String, newEntry: WeightEntry) {}
    },
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
            val isHcSupported = healthConnectManager.isSupported()
            val isStepsEnabled = userSettingsRepository.isHealthConnectStepsSyncEnabled() && healthConnectManager.hasStepsPermission()
            val lastBackup = userSettingsRepository.getLastBackupTimestamp()
            val userProfile = userSettingsRepository.getUserProfile()
            val weights = weightEntryRepository.getAll()
            val latestTrend = WeightTrend.calculateTrend(weights).lastOrNull()?.trendKg

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
                    isHealthConnectAvailable = isHcSupported,
                    isHealthConnectStepsSyncEnabled = isStepsEnabled,
                    lastBackupTimestamp = lastBackup,
                    userSex = userProfile.sex,
                    birthDate = userProfile.birthDate,
                    heightCmText = userProfile.heightCm?.let { h -> NumberFormatHelper.formatDecimal(h) } ?: "",
                    workoutKcalText = NumberFormatHelper.formatDecimal(userProfile.workoutKcalPerSession),
                    latestWeightTrendKg = latestTrend,
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

    fun onUserSexChanged(sex: UserSex) {
        _uiState.update { it.copy(userSex = sex, userSexError = null) }
    }

    fun onOpenBirthDatePicker() {
        _uiState.update { it.copy(showBirthDatePickerDialog = true) }
    }

    fun onCloseBirthDatePicker() {
        _uiState.update { it.copy(showBirthDatePickerDialog = false) }
    }

    fun onBirthDateSelected(date: LocalDate) {
        val today = LocalDate.now()
        val error = if (date.isAfter(today)) {
            UiText.StringResource(R.string.profile_error_future_birth_date)
        } else {
            val age = UserProfileValidator.calculateAge(date, today)
            when {
                age < UserProfileValidator.MIN_AGE_YEARS -> UiText.StringResource(R.string.profile_error_age_too_young)
                age > UserProfileValidator.MAX_AGE_YEARS -> UiText.StringResource(R.string.profile_error_age_too_old)
                else -> null
            }
        }
        _uiState.update {
            it.copy(
                birthDate = date,
                birthDateError = error,
                showBirthDatePickerDialog = false,
            )
        }
    }

    fun onHeightChanged(value: String) {
        val height = NumberFormatHelper.parseDecimal(value)
        val error = when {
            value.isBlank() -> UiText.StringResource(R.string.profile_error_missing_height)
            height == null || height < UserProfileValidator.MIN_HEIGHT_CM || height > UserProfileValidator.MAX_HEIGHT_CM ->
                UiText.StringResource(R.string.profile_error_height_out_of_range)
            else -> null
        }
        _uiState.update { it.copy(heightCmText = value, heightError = error) }
    }

    fun onWorkoutKcalChanged(value: String) {
        val kcal = NumberFormatHelper.parseDecimal(value)
        val error = when {
            value.isBlank() -> null // Defaults to 300
            kcal == null || kcal < UserProfileValidator.MIN_WORKOUT_KCAL || kcal > UserProfileValidator.MAX_WORKOUT_KCAL ->
                UiText.StringResource(R.string.profile_error_workout_kcal_out_of_range)
            else -> null
        }
        _uiState.update { it.copy(workoutKcalText = value, workoutKcalError = error) }
    }

    fun onSaveProfile() {
        val state = _uiState.value
        val sex = state.userSex
        val birthDate = state.birthDate
        val height = NumberFormatHelper.parseDecimal(state.heightCmText)
        val workoutKcal = NumberFormatHelper.parseDecimal(state.workoutKcalText) ?: UserProfile.DEFAULT_WORKOUT_KCAL_PER_SESSION
        val today = LocalDate.now()

        val profile = UserProfile(
            sex = sex,
            birthDate = birthDate,
            heightCm = height,
            workoutKcalPerSession = workoutKcal,
        )

        val validation = UserProfileValidator.validate(profile, today)
        if (validation is UserProfileValidationResult.Invalid) {
            val issues = validation.issues
            _uiState.update {
                it.copy(
                    userSexError = if (issues.contains(UserProfileIssue.MISSING_SEX)) UiText.StringResource(R.string.profile_error_missing_sex) else null,
                    birthDateError = when {
                        issues.contains(UserProfileIssue.MISSING_BIRTH_DATE) -> UiText.StringResource(R.string.profile_error_missing_birth_date)
                        issues.contains(UserProfileIssue.FUTURE_BIRTH_DATE) -> UiText.StringResource(R.string.profile_error_future_birth_date)
                        issues.contains(UserProfileIssue.AGE_TOO_YOUNG) -> UiText.StringResource(R.string.profile_error_age_too_young)
                        issues.contains(UserProfileIssue.AGE_TOO_OLD) -> UiText.StringResource(R.string.profile_error_age_too_old)
                        else -> null
                    },
                    heightError = when {
                        issues.contains(UserProfileIssue.MISSING_HEIGHT) -> UiText.StringResource(R.string.profile_error_missing_height)
                        issues.contains(UserProfileIssue.HEIGHT_OUT_OF_RANGE) -> UiText.StringResource(R.string.profile_error_height_out_of_range)
                        else -> null
                    },
                    workoutKcalError = if (issues.contains(UserProfileIssue.WORKOUT_KCAL_OUT_OF_RANGE)) UiText.StringResource(R.string.profile_error_workout_kcal_out_of_range) else null,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true) }
            try {
                userSettingsRepository.setUserProfile(profile)
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        userSexError = null,
                        birthDateError = null,
                        heightError = null,
                        workoutKcalError = null,
                    )
                }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.settings_profile_saved)))
            } catch (_: Exception) {
                _uiState.update { it.copy(isSavingProfile = false) }
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

    fun getHealthConnectPermissions(): Set<String> = healthConnectManager.getPermissions()

    fun onToggleHealthConnectSteps(enabled: Boolean) {
        if (enabled) {
            if (!healthConnectManager.isSupported()) {
                viewModelScope.launch {
                    _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.settings_health_connect_not_supported)))
                }
                return
            }
            _uiState.update { it.copy(showHealthConnectRationaleDialog = true) }
        } else {
            viewModelScope.launch {
                userSettingsRepository.setHealthConnectStepsSyncEnabled(false)
                _uiState.update { it.copy(isHealthConnectStepsSyncEnabled = false) }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.health_connect_sync_disabled_feedback)))
            }
        }
    }

    fun onDismissHealthConnectRationale() {
        _uiState.update { it.copy(showHealthConnectRationaleDialog = false) }
    }

    fun onConfirmHealthConnectRationale() {
        _uiState.update { it.copy(showHealthConnectRationaleDialog = false) }
    }

    fun onHealthConnectPermissionsResult(granted: Boolean) {
        viewModelScope.launch {
            if (granted) {
                userSettingsRepository.setHealthConnectStepsSyncEnabled(true)
                _uiState.update { it.copy(isHealthConnectStepsSyncEnabled = true) }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.health_connect_sync_enabled_feedback)))
                val steps = healthConnectManager.readYesterdaySteps()
                if (steps != null && steps > 0) {
                    val yesterday = LocalDate.now().minusDays(1)
                    dailyLogRepository.setSteps(yesterday.toString(), steps.toInt())
                }
            } else {
                userSettingsRepository.setHealthConnectStepsSyncEnabled(false)
                _uiState.update { it.copy(isHealthConnectStepsSyncEnabled = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.health_connect_permission_denied_feedback)))
            }
        }
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    // -------------------------------------------------------------------------
    // Backup & Restore (Phase 7.2)
    // -------------------------------------------------------------------------

    private var pendingExportPasswordChars: CharArray? = null
    private var pendingImportPayload: BackupPayload? = null

    fun onExportClicked() {
        _uiState.update {
            it.copy(
                isExportPasswordDialogOpen = true,
                exportPasswordText = "",
                exportConfirmPasswordText = "",
                exportPasswordError = null,
            )
        }
    }

    fun onExportPasswordChanged(password: String) {
        _uiState.update { it.copy(exportPasswordText = password, exportPasswordError = null) }
    }

    fun onExportConfirmPasswordChanged(password: String) {
        _uiState.update { it.copy(exportConfirmPasswordText = password, exportPasswordError = null) }
    }

    fun onConfirmExportPassword(onValidPassword: () -> Unit) {
        val pass = _uiState.value.exportPasswordText
        val confirm = _uiState.value.exportConfirmPasswordText
        if (!BackupCodec.isPasswordValid(pass)) {
            _uiState.update { it.copy(exportPasswordError = UiText.StringResource(R.string.backup_password_too_short)) }
            return
        }
        if (pass != confirm) {
            _uiState.update { it.copy(exportPasswordError = UiText.StringResource(R.string.backup_password_mismatch)) }
            return
        }
        pendingExportPasswordChars = pass.toCharArray()
        _uiState.update { it.copy(isExportPasswordDialogOpen = false) }
        onValidPassword()
    }

    fun onExportUriSelected(uri: Uri?, contentResolver: ContentResolver) {
        val password = pendingExportPasswordChars
        if (uri == null || password == null) {
            pendingExportPasswordChars?.fill('\u0000')
            pendingExportPasswordChars = null
            return
        }
        val manager = backupManager ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupLoading = true) }
            try {
                val outputStream = contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("No se pudo abrir el archivo de destino")
                manager.exportBackup(password, outputStream)
                val now = System.currentTimeMillis()
                userSettingsRepository.setLastBackupTimestamp(now)
                _uiState.update { it.copy(isBackupLoading = false, lastBackupTimestamp = now) }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.backup_export_success)))
            } catch (e: Exception) {
                _uiState.update { it.copy(isBackupLoading = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString(e.message ?: "Error al exportar")))
            } finally {
                pendingExportPasswordChars?.fill('\u0000')
                pendingExportPasswordChars = null
            }
        }
    }

    fun onImportUriSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.update {
            it.copy(
                pendingImportUri = uri,
                isImportPasswordDialogOpen = true,
                importPasswordText = "",
                importPasswordError = null,
            )
        }
    }

    fun onImportPasswordChanged(password: String) {
        _uiState.update { it.copy(importPasswordText = password, importPasswordError = null) }
    }

    fun onConfirmImportPassword(contentResolver: ContentResolver) {
        val uri = _uiState.value.pendingImportUri ?: return
        val password = _uiState.value.importPasswordText
        if (!BackupCodec.isPasswordValid(password)) {
            _uiState.update { it.copy(importPasswordError = UiText.StringResource(R.string.backup_password_too_short)) }
            return
        }
        val manager = backupManager ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupLoading = true, importPasswordError = null) }
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")
                val (summary, payload) = manager.inspectBackup(password.toCharArray(), inputStream)
                pendingImportPayload = payload
                _uiState.update {
                    it.copy(
                        isBackupLoading = false,
                        isImportPasswordDialogOpen = false,
                        restoreSummary = summary,
                        isRestoreConfirmDialogOpen = true,
                    )
                }
            } catch (e: InvalidPasswordOrCorruptException) {
                _uiState.update {
                    it.copy(
                        isBackupLoading = false,
                        importPasswordError = UiText.StringResource(R.string.backup_error_bad_password_or_corrupt),
                    )
                }
            } catch (e: NewerSchemaVersionException) {
                _uiState.update {
                    it.copy(
                        isBackupLoading = false,
                        isImportPasswordDialogOpen = false,
                        pendingImportUri = null,
                    )
                }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.backup_error_newer_schema)))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBackupLoading = false,
                        importPasswordError = UiText.DynamicString(e.message ?: "Error al descifrar"),
                    )
                }
            }
        }
    }

    fun onConfirmRestore(onRestoredSuccess: () -> Unit) {
        val payload = pendingImportPayload ?: return
        val manager = backupManager ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupLoading = true, isRestoreConfirmDialogOpen = false) }
            try {
                manager.restoreBackup(payload)
                loadSettings()
                _uiState.update {
                    it.copy(
                        isBackupLoading = false,
                        restoreSummary = null,
                        pendingImportUri = null,
                    )
                }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.backup_restore_success)))
                onRestoredSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isBackupLoading = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString(e.message ?: "Error al restaurar")))
            } finally {
                pendingImportPayload = null
            }
        }
    }

    fun onDismissBackupDialogs() {
        pendingExportPasswordChars?.fill('\u0000')
        pendingExportPasswordChars = null
        pendingImportPayload = null
        _uiState.update {
            it.copy(
                isExportPasswordDialogOpen = false,
                isImportPasswordDialogOpen = false,
                isRestoreConfirmDialogOpen = false,
                exportPasswordError = null,
                importPasswordError = null,
                pendingImportUri = null,
                restoreSummary = null,
            )
        }
    }
}
