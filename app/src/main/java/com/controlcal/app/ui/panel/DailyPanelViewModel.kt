package com.controlcal.app.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.DayType
import com.controlcal.core.model.toMacroTargets
import com.controlcal.core.nutrition.CalculateDayRemainingUseCase
import com.controlcal.core.nutrition.CopyDayUseCase
import com.controlcal.core.nutrition.GetActiveGoalProfileUseCase
import com.controlcal.core.nutrition.MealClassifier
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.nutrition.MealTimeCalculator
import com.controlcal.core.nutrition.RepeatMealUseCase
import com.controlcal.core.nutrition.ResolveDayTypeUseCase
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.repo.DailyLogRepository
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import com.controlcal.core.repo.UserSettingsRepository
import com.controlcal.core.repo.WeightEntryRepository
import com.controlcal.core.suggest.QuickLogRanker
import com.controlcal.core.suggest.QuickLogSuggestion
import com.controlcal.core.tdee.DailyIntake
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.tdee.TdeeCalculationResult
import com.controlcal.core.tdee.TdeeEstimator
import com.controlcal.core.tdee.TdeeGoalProposal
import com.controlcal.core.tdee.TdeeProposalCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.controlcal.core.date.DateHelper
import com.controlcal.core.repo.MealConfigRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class DailyPanelViewModel @Inject constructor(
    private val mealEntryRepository: MealEntryRepository,
    private val productRepository: ProductRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val resolveDayTypeUseCase: ResolveDayTypeUseCase,
    private val getActiveGoalProfileUseCase: GetActiveGoalProfileUseCase,
    private val calculateDayRemainingUseCase: CalculateDayRemainingUseCase = CalculateDayRemainingUseCase(),
    private val weightEntryRepository: WeightEntryRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val goalProfileRepository: GoalProfileRepository,
    private val mealConfigRepository: MealConfigRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val repeatMealUseCase = RepeatMealUseCase()
    private val copyDayUseCase = CopyDayUseCase()

    private val _uiState = MutableStateFlow(DailyPanelUiState())
    val uiState: StateFlow<DailyPanelUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

    fun sendFeedback(event: UiFeedbackEvent) {
        viewModelScope.launch {
            _feedbackEvents.send(event)
        }
    }

    init {
        loadDay(DateHelper.getToday(clock))
    }

    fun loadDay(date: LocalDate) {
        viewModelScope.launch {
            val today = DateHelper.getToday(clock)
            _uiState.update { it.copy(isLoading = true, selectedDate = date, today = today) }

            val dateStr = date.toString()
            val entries = mealEntryRepository.getByDate(dateStr)
            val totals = NutritionCalculator.calculateDayTotals(dateStr, entries)

            val configuredMeals = mealConfigRepository.getMealConfigs()
            val activeConfigs = configuredMeals.filter { it.isActive }.sortedBy { it.startTime }
            val inactiveConfigs = configuredMeals.filter { !it.isActive }
            val configById = configuredMeals.associateBy { it.id }

            // Inactive meals that have entries on this day
            val inactiveWithEntries = inactiveConfigs.filter { inactive ->
                entries.any { it.meal == inactive.id }
            }

            // All configured meals to show on this day, ordered by start time
            val mealsToShow = (activeConfigs + inactiveWithEntries).sortedBy { it.startTime }

            val mealSections = mutableListOf<MealSectionUiModel>()
            for (meal in mealsToShow) {
                val mealEntries = entries.filter { it.meal == meal.id }
                mealSections.add(
                    MealSectionUiModel(
                        mealId = meal.id,
                        name = meal.name,
                        startTime = meal.startTime,
                        isInactive = !meal.isActive,
                        isUnassigned = false,
                        entries = mealEntries,
                        totalKcal = NutritionCalculator.calculateTotals(mealEntries).kcal,
                        summary = com.controlcal.core.nutrition.MealSummaryFormatter.formatSummary(mealEntries),
                    )
                )
            }

            // Entries with an unknown meal id go to "Sin asignar"
            val unknownEntries = entries.filter { it.meal !in configById }
            if (unknownEntries.isNotEmpty()) {
                mealSections.add(
                    MealSectionUiModel(
                        mealId = UNASSIGNED_MEAL_ID,
                        name = "Sin asignar",
                        startTime = null,
                        isInactive = false,
                        isUnassigned = true,
                        entries = unknownEntries,
                        totalKcal = NutritionCalculator.calculateTotals(unknownEntries).kcal,
                        summary = com.controlcal.core.nutrition.MealSummaryFormatter.formatSummary(unknownEntries),
                    )
                )
            }

            val entriesByMeal = (configuredMeals.map { it.id } + listOf(UNASSIGNED_MEAL_ID)).associateWith { mealId ->
                entries.filter { it.meal == mealId }
            }

            val dayType = resolveDayTypeUseCase(date)
            val activeProfile = getActiveGoalProfileUseCase(dayType, dateStr)
            val targets = activeProfile.toMacroTargets()
            val remaining = calculateDayRemainingUseCase(totals.totals, targets)

            // QuickLogRanker suggestions for current time of day
            val nowMillis = clock.millis()
            val sixtyDaysAgo = nowMillis - (60L * 24L * 60L * 60L * 1000L)
            val history = mealEntryRepository.getSince(sixtyDaysAgo)
            val activeProducts = productRepository.getActiveProducts().map { it.id }.toSet()
            val suggestions = QuickLogRanker.rank(
                entries = history,
                nowMillis = nowMillis,
                activeProductIds = activeProducts,
                limit = 6,
            )

            val currentTime = LocalTime.now(clock)
            val suggested = MealClassifier.suggestMeal(configuredMeals, currentTime)
            val currentSlotMeal = suggested?.id ?: activeConfigs.firstOrNull()?.id ?: DefaultMealIds.BREAKFAST
            val currentSlotMealName = suggested?.name ?: activeConfigs.firstOrNull()?.name ?: ""

            // Weekly TDEE Proposal check (section 5.5 of docs/PLAN.md)
            val todayStr = today.toString()
            val lastReviewedStr = userSettingsRepository.getLastTdeeProposalReviewedDate()
            val shouldCheckProposal = if (lastReviewedStr == null) {
                true
            } else {
                try {
                    val lastReviewed = LocalDate.parse(lastReviewedStr)
                    ChronoUnit.DAYS.between(lastReviewed, today) >= 7
                } catch (_: Exception) {
                    true
                }
            }

            var tdeeProposal: TdeeGoalProposal? = null
            var isWeeklyProposalVisible = false
            var tdeeResult: TdeeCalculationResult? = null

            if (shouldCheckProposal) {
                val startDate = today.minusDays((TdeeEstimator.WINDOW_DAYS - 1).toLong())
                val dailyIntakes = (0 until TdeeEstimator.WINDOW_DAYS).map { offset ->
                    val d = startDate.plusDays(offset.toLong()).toString()
                    val dayEntries = mealEntryRepository.getByDate(d)
                    val dayTotals = NutritionCalculator.calculateDayTotals(d, dayEntries)
                    DailyIntake(date = d, kcal = dayTotals.totals.kcal, isComplete = dayEntries.isNotEmpty())
                }
                val weights = weightEntryRepository.getAll()

                val result = TdeeEstimator.estimate(todayStr, dailyIntakes, weights)
                tdeeResult = result
                if (result is TdeeCalculationResult.Success) {
                    val minKcalLimit = userSettingsRepository.getMinKcalLimit()
                    val proposal = TdeeProposalCalculator.calculateProposal(
                        currentKcal = activeProfile.kcal,
                        estimatedTdee = result.tdeeKcal,
                        minKcalLimit = minKcalLimit,
                    )
                    tdeeProposal = proposal
                    isWeeklyProposalVisible = true
                }
            }

            _uiState.update {
                it.copy(
                    selectedDate = date,
                    dayTotals = totals,
                    entriesByMeal = entriesByMeal,
                    mealSections = mealSections,
                    suggestions = suggestions,
                    currentSlotMeal = currentSlotMeal,
                    currentSlotMealName = currentSlotMealName,
                    dayType = dayType,
                    activeGoalProfile = activeProfile,
                    dayRemaining = remaining,
                    tdeeProposal = tdeeProposal,
                    isWeeklyProposalVisible = isWeeklyProposalVisible,
                    tdeeCalculationResult = tdeeResult,
                    isLoading = false,
                )
            }
        }
    }

    fun onAcceptTdeeProposal() {
        val proposal = _uiState.value.tdeeProposal ?: return
        viewModelScope.launch {
            val today = DateHelper.getToday(clock)
            val todayStr = today.toString()
            val delta = proposal.deltaKcal
            val minKcal = proposal.minKcalLimit

            val currentTraining = getActiveGoalProfileUseCase(DayType.TRAINING, todayStr)
            val currentRest = getActiveGoalProfileUseCase(DayType.REST, todayStr)

            val newTrainingKcal = maxOf(minKcal, currentTraining.kcal + delta)
            val newRestKcal = maxOf(minKcal, currentRest.kcal + delta)

            val trainingRatio = if (currentTraining.kcal > 0) newTrainingKcal / currentTraining.kcal else 1.0
            val restRatio = if (currentRest.kcal > 0) newRestKcal / currentRest.kcal else 1.0

            val newTrainingProfile = GoalProfile(
                dayType = DayType.TRAINING,
                kcal = newTrainingKcal,
                proteinG = (currentTraining.proteinG * trainingRatio * 10).roundToInt() / 10.0,
                carbsG = (currentTraining.carbsG * trainingRatio * 10).roundToInt() / 10.0,
                fatG = (currentTraining.fatG * trainingRatio * 10).roundToInt() / 10.0,
                validFrom = todayStr,
                origin = GoalOrigin.TDEE,
            )

            val newRestProfile = GoalProfile(
                dayType = DayType.REST,
                kcal = newRestKcal,
                proteinG = (currentRest.proteinG * restRatio * 10).roundToInt() / 10.0,
                carbsG = (currentRest.carbsG * restRatio * 10).roundToInt() / 10.0,
                fatG = (currentRest.fatG * restRatio * 10).roundToInt() / 10.0,
                validFrom = todayStr,
                origin = GoalOrigin.TDEE,
            )

            goalProfileRepository.insert(newTrainingProfile)
            goalProfileRepository.insert(newRestProfile)
            userSettingsRepository.setLastTdeeProposalReviewedDate(todayStr)

            _uiState.update {
                it.copy(
                    isWeeklyProposalVisible = false,
                    tdeeProposal = null,
                    message = "Nuevo objetivo aplicado correctamente",
                )
            }
            loadDay(_uiState.value.selectedDate)
        }
    }

    fun onRejectTdeeProposal() {
        viewModelScope.launch {
            val todayStr = DateHelper.getToday(clock).toString()
            userSettingsRepository.setLastTdeeProposalReviewedDate(todayStr)
            _uiState.update {
                it.copy(
                    isWeeklyProposalVisible = false,
                    tdeeProposal = null,
                    message = "Propuesta rechazada para esta semana",
                )
            }
        }
    }

    fun onDayTypeChanged(newDayType: DayType) {
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val dateStr = date.toString()
            dailyLogRepository.setDayType(dateStr, newDayType)

            val activeProfile = getActiveGoalProfileUseCase(newDayType, dateStr)
            val targets = activeProfile.toMacroTargets()
            val remaining = calculateDayRemainingUseCase(_uiState.value.dayTotals.totals, targets)

            _uiState.update {
                it.copy(
                    dayType = newDayType,
                    activeGoalProfile = activeProfile,
                    dayRemaining = remaining,
                )
            }
            val msgRes = if (newDayType == DayType.TRAINING) {
                R.string.feedback_day_type_training
            } else {
                R.string.feedback_day_type_rest
            }
            _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(msgRes)))
        }
    }

    fun onSuggestionTapped(suggestion: QuickLogSuggestion) {
        viewModelScope.launch {
            val product = productRepository.getById(suggestion.productId) ?: return@launch
            val portion = NutritionCalculator.calculatePortionMacros(product, suggestion.defaultGrams)
            val targetDate = _uiState.value.selectedDate
            val targetMeal = suggestion.targetMeal
            val existingEntries = mealEntryRepository.getByDateAndMeal(targetDate.toString(), targetMeal)
            val configuredStartTime = mealConfigRepository.getMealConfigs().find { it.id == targetMeal }?.startTime
            val loggedAt = MealTimeCalculator.calculateLoggedAt(
                date = targetDate,
                existingEntries = existingEntries,
                configuredStartTime = configuredStartTime,
                zoneId = clock.zone,
                clock = clock,
            )

            val entry = MealEntry(
                id = 0L,
                date = targetDate.toString(),
                meal = targetMeal,
                productId = product.id,
                productName = product.name,
                grams = suggestion.defaultGrams,
                kcal = portion.kcal,
                protein = portion.protein,
                carbs = portion.carbs,
                fat = portion.fat,
                loggedAt = loggedAt,
            )

            val result = mealEntryRepository.save(entry)
            loadDay(_uiState.value.selectedDate)
            if (result is com.controlcal.core.nutrition.MealEntrySaveResult.Merged) {
                val formattedQuantity = com.controlcal.core.nutrition.FoodPortionFormatter.formatQuantity(
                    grams = result.mergedEntry.grams,
                    unitG = product.unitG,
                    unitName = product.unitName,
                )
                _uiState.update {
                    it.copy(
                        recentlyMergedPreviousEntry = result.previousEntry,
                        message = null,
                    )
                }
                _feedbackEvents.send(
                    UiFeedbackEvent.Success(
                        message = UiText.StringResource(
                            R.string.meal_entry_merged_snackbar,
                            product.name,
                            formattedQuantity,
                        ),
                        actionLabel = UiText.StringResource(R.string.feedback_action_undo),
                        onAction = { onUndoMerge() },
                    )
                )
            } else {
                val mealName = _uiState.value.mealSections.find { it.mealId == targetMeal }?.name ?: targetMeal
                _uiState.update {
                    it.copy(
                        recentlyMergedPreviousEntry = null,
                        message = "Añadido al diario: ${product.name} (${NumberFormatHelper.formatDecimal(suggestion.defaultGrams)} g)",
                    )
                }
                _feedbackEvents.send(
                    UiFeedbackEvent.Success(
                        message = UiText.StringResource(
                            R.string.daily_panel_quick_add_success,
                            product.name,
                            NumberFormatHelper.formatDecimal(suggestion.defaultGrams),
                            mealName,
                        )
                    )
                )
            }
        }
    }

    fun onSuggestionLongPressed(suggestion: QuickLogSuggestion) {
        viewModelScope.launch {
            val product = productRepository.getById(suggestion.productId)
            val grams = suggestion.defaultGrams
            val gramsStr = NumberFormatHelper.formatDecimal(grams)
            val portion = if (product != null) {
                NutritionCalculator.calculatePortionMacros(product, grams)
            } else null

            _uiState.update {
                it.copy(
                    editingSuggestion = suggestion,
                    editingGramsText = gramsStr,
                    editingGramsError = null,
                    portionPreviewKcal = portion?.kcal ?: 0.0,
                    portionPreviewProtein = portion?.protein ?: 0.0,
                    portionPreviewCarbs = portion?.carbs ?: 0.0,
                    portionPreviewFat = portion?.fat ?: 0.0,
                )
            }
        }
    }

    fun onEditingGramsChanged(text: String) {
        val parsed = NumberFormatHelper.parseDecimal(text)
        val error = when {
            text.isBlank() -> "Campo obligatorio"
            parsed == null -> "Número no válido"
            parsed <= 0.0 -> "Debe ser mayor que 0"
            else -> null
        }

        viewModelScope.launch {
            val suggestion = _uiState.value.editingSuggestion
            val product = suggestion?.let { productRepository.getById(it.productId) }
            val portion = if (product != null && parsed != null && parsed > 0.0) {
                NutritionCalculator.calculatePortionMacros(product, parsed)
            } else null

            _uiState.update {
                it.copy(
                    editingGramsText = text,
                    editingGramsError = error,
                    portionPreviewKcal = portion?.kcal ?: 0.0,
                    portionPreviewProtein = portion?.protein ?: 0.0,
                    portionPreviewCarbs = portion?.carbs ?: 0.0,
                    portionPreviewFat = portion?.fat ?: 0.0,
                )
            }
        }
    }

    fun onConfirmQuickLog() {
        val state = _uiState.value
        val suggestion = state.editingSuggestion ?: return
        val parsedGrams = NumberFormatHelper.parseDecimal(state.editingGramsText)

        if (parsedGrams == null || parsedGrams <= 0.0) {
            _uiState.update {
                it.copy(editingGramsError = "Introduce una cantidad válida")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val product = productRepository.getById(suggestion.productId) ?: return@launch
                val portion = NutritionCalculator.calculatePortionMacros(product, parsedGrams)
                val targetDate = state.selectedDate
                val targetMeal = suggestion.targetMeal
                val existingEntries = mealEntryRepository.getByDateAndMeal(targetDate.toString(), targetMeal)
                val configuredStartTime = mealConfigRepository.getMealConfigs().find { it.id == targetMeal }?.startTime
                val loggedAt = MealTimeCalculator.calculateLoggedAt(
                    date = targetDate,
                    existingEntries = existingEntries,
                    configuredStartTime = configuredStartTime,
                    zoneId = clock.zone,
                    clock = clock,
                )

                val entry = MealEntry(
                    id = 0L,
                    date = targetDate.toString(),
                    meal = targetMeal,
                    productId = product.id,
                    productName = product.name,
                    grams = parsedGrams,
                    kcal = portion.kcal,
                    protein = portion.protein,
                    carbs = portion.carbs,
                    fat = portion.fat,
                    loggedAt = loggedAt,
                )

                val result = mealEntryRepository.save(entry)
                _uiState.update { it.copy(isSaving = false, editingSuggestion = null) }
                loadDay(state.selectedDate)
                if (result is com.controlcal.core.nutrition.MealEntrySaveResult.Merged) {
                    val formattedQuantity = com.controlcal.core.nutrition.FoodPortionFormatter.formatQuantity(
                        grams = result.mergedEntry.grams,
                        unitG = product.unitG,
                        unitName = product.unitName,
                    )
                    _uiState.update {
                        it.copy(
                            recentlyMergedPreviousEntry = result.previousEntry,
                            message = null,
                        )
                    }
                    _feedbackEvents.send(
                        UiFeedbackEvent.Success(
                            message = UiText.StringResource(
                                R.string.meal_entry_merged_snackbar,
                                product.name,
                                formattedQuantity,
                            ),
                            actionLabel = UiText.StringResource(R.string.feedback_action_undo),
                            onAction = { onUndoMerge() },
                        )
                    )
                } else {
                    val mealName = _uiState.value.mealSections.find { it.mealId == targetMeal }?.name ?: targetMeal
                    _uiState.update {
                        it.copy(
                            recentlyMergedPreviousEntry = null,
                            message = null,
                        )
                    }
                    _feedbackEvents.send(
                        UiFeedbackEvent.Success(
                            message = UiText.StringResource(
                                R.string.daily_panel_quick_add_success,
                                product.name,
                                NumberFormatHelper.formatDecimal(parsedGrams),
                                mealName,
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _feedbackEvents.send(
                    UiFeedbackEvent.Error(
                        UiText.StringResource(R.string.feedback_save_error)
                    )
                )
            }
        }
    }

    fun onDismissEditDialog() {
        _uiState.update { it.copy(editingSuggestion = null) }
    }

    fun notifyAdded(entry: MealEntry) {
        viewModelScope.launch {
            val product = entry.productId?.let { productRepository.getById(it) }
            val formattedQuantity = com.controlcal.core.nutrition.FoodPortionFormatter.formatQuantity(
                grams = entry.grams,
                unitG = product?.unitG,
                unitName = product?.unitName,
            )
            loadDay(_uiState.value.selectedDate)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.StringResource(
                        R.string.feedback_food_added,
                        entry.productName,
                        formattedQuantity,
                    )
                )
            )
        }
    }

    fun notifyMerged(mergedEntry: MealEntry, previousEntry: MealEntry) {
        viewModelScope.launch {
            val product = mergedEntry.productId?.let { productRepository.getById(it) }
            val formattedQuantity = com.controlcal.core.nutrition.FoodPortionFormatter.formatQuantity(
                grams = mergedEntry.grams,
                unitG = product?.unitG,
                unitName = product?.unitName,
            )
            _uiState.update {
                it.copy(
                    recentlyMergedPreviousEntry = previousEntry,
                    message = null,
                )
            }
            loadDay(_uiState.value.selectedDate)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.StringResource(
                        R.string.meal_entry_merged_snackbar,
                        mergedEntry.productName,
                        formattedQuantity,
                    ),
                    actionLabel = UiText.StringResource(R.string.feedback_action_undo),
                    onAction = { onUndoMerge() },
                )
            )
        }
    }

    fun onUndoMerge() {
        val previous = _uiState.value.recentlyMergedPreviousEntry ?: return
        viewModelScope.launch {
            mealEntryRepository.update(previous)
            _uiState.update {
                it.copy(
                    recentlyMergedPreviousEntry = null,
                    message = null,
                )
            }
            loadDay(_uiState.value.selectedDate)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.DynamicString("Acción deshecha")
                )
            )
        }
    }

    fun onRepeatMeal(meal: String) {
        viewModelScope.launch {
            val dateStr = _uiState.value.selectedDate.toString()
            val latestDate = mealEntryRepository.getLatestDateWithMealBefore(dateStr, meal)
            if (latestDate == null) {
                _uiState.update { it.copy(message = "No hay registros anteriores de esta comida") }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.daily_panel_repeat_meal_empty)))
                return@launch
            }

            val sourceEntries = mealEntryRepository.getByDateAndMeal(latestDate, meal)
            if (sourceEntries.isEmpty()) {
                _uiState.update { it.copy(message = "No hay registros anteriores de esta comida") }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.daily_panel_repeat_meal_empty)))
                return@launch
            }

            val cloned = repeatMealUseCase(sourceEntries, dateStr, meal, clock.zone)
            mealEntryRepository.saveAll(cloned)
            loadDay(_uiState.value.selectedDate)
            _uiState.update { it.copy(message = "Comida repetida de un día anterior") }
            _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.daily_panel_repeat_meal_success)))
        }
    }

    fun onCopyYesterday() {
        viewModelScope.launch {
            val yesterdayStr = _uiState.value.selectedDate.minusDays(1).toString()
            val sourceEntries = mealEntryRepository.getByDate(yesterdayStr)
            if (sourceEntries.isEmpty()) {
                _uiState.update { it.copy(message = "No hay registros en el día de ayer") }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.daily_panel_copy_yesterday_empty)))
                return@launch
            }

            val targetDateStr = _uiState.value.selectedDate.toString()
            val cloned = copyDayUseCase(sourceEntries, targetDateStr, clock.zone)
            mealEntryRepository.saveAll(cloned)
            loadDay(_uiState.value.selectedDate)
            _uiState.update { it.copy(message = "Se han copiado las comidas de ayer") }
            _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.daily_panel_copy_yesterday_success)))
        }
    }

    fun onPreviousDay() {
        loadDay(_uiState.value.selectedDate.minusDays(1))
    }

    fun onNextDay() {
        loadDay(_uiState.value.selectedDate.plusDays(1))
    }

    fun onSelectToday() {
        loadDay(DateHelper.getToday(clock))
    }

    fun onDateSelected(date: LocalDate) {
        loadDay(date)
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        const val UNASSIGNED_MEAL_ID = "UNASSIGNED"
    }
}
